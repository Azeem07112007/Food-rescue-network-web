package com.foodrescue;

import com.foodrescue.config.DBConnection;
import com.foodrescue.model.User;
import com.foodrescue.model.enums.UserType;
import com.foodrescue.service.*;
import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** HTTP adapter for the existing service/repository backend. */
public final class WebApplication {
    private static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(LocalDateTime.class, new TypeAdapter<LocalDateTime>() {
            @Override public void write(JsonWriter out, LocalDateTime value) throws IOException {
                if (value == null) out.nullValue(); else out.value(value.toString());
            }
            @Override public LocalDateTime read(JsonReader in) throws IOException {
                if (in.peek() == com.google.gson.stream.JsonToken.NULL) { in.nextNull(); return null; }
                return LocalDateTime.parse(in.nextString().replace("Z", ""), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }
        }.nullSafe())
        .create();
    private final UserService users = new UserService();
    private final DonationService donations = new DonationService();
    private final PickupRequestService requests = new PickupRequestService();
    private final FoodAlertService alerts = new FoodAlertService();
    private final FoodMatchingService matching = new FoodMatchingService();
    private final DeadlineService deadlines = new DeadlineService();

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        WebApplication app = new WebApplication();
        server.createContext("/api", app::handleApi);
        server.createContext("/", app::serveStatic);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
        System.out.println("Food Rescue Network web app running at http://localhost:" + port);
        System.out.println("MySQL: " + DBConnection.testConnection().getMessage());
    }

    private void handleApi(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            String path = ex.getRequestURI().getPath().substring(4);
            Map<String,String> q = query(ex.getRequestURI().getRawQuery());
            JsonObject body = readBody(ex);
            Object result;
            if (method.equals("OPTIONS")) { send(ex,204,""); return; }
            if (path.equals("/health") && method.equals("GET")) {
                DBConnection.ConnectionTestResult r = DBConnection.testConnection();
                sendJson(ex, r.isSuccess()?200:503, Map.of("ok",r.isSuccess(),"message",r.getMessage())); return;
            }
            if (path.equals("/users") && method.equals("GET")) result=users.getAllUsers();
            else if (path.equals("/users") && method.equals("POST")) {
                UserType role=UserType.fromString(str(body,"userType"));
                if(role==null) throw new IllegalArgumentException("Choose a valid user role.");
                result=users.registerUser(str(body,"userName"),str(body,"email"),str(body,"contactInfo"),role,str(body,"location"));
                sendJson(ex,201,result); return;
            }
            else if (path.equals("/donations") && method.equals("GET")) {
                String category=q.get("category"), location=q.get("location");
                Integer min=q.containsKey("minQuantity")&&!q.get("minQuantity").isBlank()?Integer.valueOf(q.get("minQuantity")):null;
                result=donations.searchAvailable(blankNull(category),min,blankNull(location));
            }
            else if (path.equals("/donations/mine") && method.equals("GET")) result=donations.getDonationsByProvider(integer(q,"userId"));
            else if (path.equals("/donations") && method.equals("POST")) {
                LocalDateTime from=LocalDateTime.parse(str(body,"availableFrom"));
                LocalDateTime deadline=LocalDateTime.parse(str(body,"pickupDeadline"));
                result=donations.publishDonation(integer(body,"providerId"),str(body,"foodName"),str(body,"foodCategory"),integer(body,"quantity"),str(body,"pickupLocation"),from,deadline);
                sendJson(ex,201,result); return;
            }
            else if (path.equals("/matches") && method.equals("GET")) {
                String loc=q.get("location"); Integer qty=q.containsKey("quantity")?Integer.valueOf(q.get("quantity")):null;
                Double hours=q.containsKey("hours")?Double.valueOf(q.get("hours")):null;
                result=matching.findMatches(loc==null?"":loc,qty,hours);
            }
            else if (path.equals("/requests") && method.equals("GET")) {
                User u=users.getUserById(integer(q,"userId"));
                if(u==null) throw new IllegalArgumentException("User not found.");
                result=u.getUserType().isProvider()?requests.getRequestsForProvider(u.getUserId()):requests.getRequestsForRequester(u.getUserId());
            }
            else if (path.equals("/requests") && method.equals("POST")) {
                result=requests.createPickupRequest(integer(body,"donationId"),integer(body,"requesterId"),integer(body,"requestedQuantity"),str(body,"remarks"));
                sendJson(ex,201,result); return;
            }
            else if (path.matches("/requests/\\d+/approve") && method.equals("POST")) { requests.approveRequest(id(path),integer(body,"actingUserId")); result=Map.of("message","Pickup request approved."); }
            else if (path.matches("/requests/\\d+/reject") && method.equals("POST")) { requests.rejectRequest(id(path),integer(body,"actingUserId"),str(body,"reason")); result=Map.of("message","Pickup request rejected."); }
            else if (path.matches("/requests/\\d+/cancel") && method.equals("POST")) { requests.cancelRequest(id(path),integer(body,"actingUserId")); result=Map.of("message","Pickup request cancelled."); }
            else if (path.matches("/requests/\\d+/complete") && method.equals("POST")) { requests.completePickup(id(path),integer(body,"actingUserId")); result=Map.of("message","Pickup marked as completed."); }
            else if (path.equals("/alerts") && method.equals("GET")) result=alerts.getAlertsForUser(integer(q,"userId"),Boolean.parseBoolean(q.getOrDefault("unreadOnly","false")));
            else if (path.matches("/alerts/\\d+/read") && method.equals("POST")) result=Map.of("updated",alerts.markAsRead(id(path),integer(body,"userId")));
            else if (path.equals("/alerts/read-all") && method.equals("POST")) result=Map.of("updated",alerts.markAllAsRead(integer(body,"userId")));
            else if (path.equals("/maintenance") && method.equals("POST")) { deadlines.runAllDeadlineChecks(); result=Map.of("message","Deadline checks completed."); }
            else { sendJson(ex,404,Map.of("error","API route not found: "+method+" "+path)); return; }
            sendJson(ex,200,result);
        } catch (IllegalArgumentException e) { sendJson(ex,400,Map.of("error",safeMessage(e))); }
          catch (Exception e) { sendJson(ex,400,Map.of("error",safeMessage(e))); }
    }

    private void serveStatic(HttpExchange ex) throws IOException {
        String path=ex.getRequestURI().getPath(); if(path.equals("/")) path="/index.html";
        if(path.contains("..") || path.contains("\\")) { send(ex,400,"Bad path"); return; }
        try(InputStream in=getClass().getClassLoader().getResourceAsStream("web"+path)) {
            if(in==null){ send(ex,404,"Not found"); return; }
            byte[] data=in.readAllBytes(); String type=path.endsWith(".css")?"text/css; charset=utf-8":path.endsWith(".js")?"application/javascript; charset=utf-8":path.endsWith(".svg")?"image/svg+xml":"text/html; charset=utf-8";
            ex.getResponseHeaders().set("Content-Type",type); ex.getResponseHeaders().set("X-Content-Type-Options","nosniff"); ex.sendResponseHeaders(200,data.length); try(OutputStream o=ex.getResponseBody()){o.write(data);}
        }
    }
    private static JsonObject readBody(HttpExchange ex) throws IOException { if(ex.getRequestBody()==null)return new JsonObject(); try(Reader r=new InputStreamReader(ex.getRequestBody(),StandardCharsets.UTF_8)){JsonElement e=JsonParser.parseReader(r);return e.isJsonObject()?e.getAsJsonObject():new JsonObject();}catch(JsonParseException e){return new JsonObject();} }
    private static Map<String,String> query(String raw){Map<String,String> m=new HashMap<>();if(raw!=null)for(String pair:raw.split("&")){String[] p=pair.split("=",2);m.put(decode(p[0]),p.length>1?decode(p[1]):"");}return m;}
    private static String decode(String s){try{return java.net.URLDecoder.decode(s,StandardCharsets.UTF_8);}catch(Exception e){return s;}}
    private static String str(JsonObject o,String k){JsonElement e=o.get(k);return e==null||e.isJsonNull()?"":e.getAsString().trim();}
    private static int integer(JsonObject o,String k){return Integer.parseInt(str(o,k));}
    private static int integer(Map<String,String> o,String k){return Integer.parseInt(o.getOrDefault(k,"0"));}
    private static String blankNull(String s){return s==null||s.isBlank()?null:s;}
    private static int id(String path){String[] p=path.split("/");return Integer.parseInt(p[2]);}
    private static String safeMessage(Exception e){String m=e.getMessage();return m==null||m.isBlank()?"Request failed. Check backend logs and database configuration.":m;}
    private static void sendJson(HttpExchange ex,int status,Object value)throws IOException{byte[] b=GSON.toJson(value).getBytes(StandardCharsets.UTF_8);ex.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");ex.getResponseHeaders().set("Cache-Control","no-store");ex.sendResponseHeaders(status,b.length);try(OutputStream o=ex.getResponseBody()){o.write(b);}}
    private static void send(HttpExchange ex,int status,String value)throws IOException{byte[] b=value.getBytes(StandardCharsets.UTF_8);ex.sendResponseHeaders(status,status==204?-1:b.length);if(status!=204)try(OutputStream o=ex.getResponseBody()){o.write(b);}}
}
