# Food Rescue Network

A complete, production-grade Java Maven backend and database application for redistributing surplus food. Built strictly with **Java 17**, **Apache Maven**, **JDBC**, and **MySQL**, adhering to a clean layered architecture, transaction safety, and comprehensive automated testing.

---

## 1. Project Purpose

Every day, vast amounts of edible surplus food are wasted by restaurants, college hostels, canteens, and event organizers, while charitable NGOs and volunteers struggle to procure food for vulnerable populations.

**Food Rescue Network** solves this coordination challenge:
- **Food Providers** (`RESTAURANT`, `HOSTEL`, `CANTEEN`, `EVENT_ORGANIZER`) publish surplus food listings with exact categories, portion quantities, pickup locations, availability windows, and hard pickup deadlines.
- **Requesters** (`NGO`, `VOLUNTEER`) search available food, receive deterministic smart recommendations based on location proximity and time urgency, and place pickup requests.
- **Transactional Inventory Management**: Atomically prevents overbooking, reserves portions on provider approval, safely restores inventory upon cancellation, and tracks collection completion.
- **Automated Alerts and Deadlines**: Application-level notifications alert requesters to new food and warn both parties of approaching deadlines, while periodic audits expire uncollected food whose deadline has passed.

---

## 2. Architecture and Responsibilities

The codebase follows a strict layered architecture with clean separation of concerns:

```
food-rescue-network/
├── pom.xml                                    # Maven configuration & dependencies
├── README.md                                  # Complete system documentation
├── .gitignore                                 # Git ignore rules (credentials & build outputs)
├── .env.example                               # Safe credential template
├── .env                                       # Local configuration file
├── mvnw.cmd                                   # Windows Maven wrapper
├── run.bat                                    # Convenient application launcher
├── database/
│   ├── schema.sql                             # DDL for users, food_donations, pickup_requests, alerts
│   ├── constraints.sql                        # Table constraints and foreign key verification queries
│   ├── verification.sql                       # Integrity and overbooking audit queries
│   └── migration_notes.sql                    # Non-destructive schema compatibility audit
└── src/
    ├── main/
    │   ├── java/
    │   │   └── com/foodrescue/
    │   │       ├── Main.java                  # Console UI loop, 16-option menu & session state
    │   │       ├── config/
    │   │       │   └── DBConnection.java      # JDBC manager, .env & properties hierarchy, connection tester
    │   │       ├── model/
    │   │       │   ├── User.java              # User domain entity (with location)
    │   │       │   ├── FoodDonation.java      # Food donation entity (quantity & remaining_quantity)
    │   │       │   ├── PickupRequest.java     # Pickup request entity
    │   │       │   ├── FoodAlert.java         # Alert notification entity (mapped to alerts table)
    │   │       │   └── enums/
    │   │       │       ├── UserType.java      # RESTAURANT, HOSTEL, CANTEEN, EVENT_ORGANIZER, NGO, VOLUNTEER
    │   │       │       ├── DonationStatus.java# AVAILABLE, RESERVED, COLLECTED, EXPIRED, CANCELLED
    │   │       │       ├── RequestStatus.java # PENDING, APPROVED, REJECTED, COMPLETED, CANCELLED
    │   │       │       └── AlertType.java     # Notification categories
    │   │       ├── controller/
    │   │       │   ├── UserController.java    # Registration and session selection controller
    │   │       │   ├── DonationController.java# Publishing, searching, and smart matching controller
    │   │       │   └── PickupRequestController.java # Requests, approvals, completions, and alerts
    │   │       ├── service/
    │   │       │   ├── UserService.java       # User business logic & duplicate email checks
    │   │       │   ├── DonationService.java   # Donation validation, publication & filtering
    │   │       │   ├── PickupRequestService.java # Transaction-safe approvals, cancellations & completions
    │   │       │   ├── FoodMatchingService.java  # Deterministic rule-based matching algorithm
    │   │       │   ├── DeadlineService.java   # Automated expiration & deadline warning alerts
    │   │       │   └── FoodAlertService.java  # Deduplicated alert creation and polling
    │   │       ├── repository/
    │   │       │   ├── UserRepository.java    # PreparedStatements for USERS table
    │   │       │   ├── DonationRepository.java# PreparedStatements & row locking for FOOD_DONATIONS
    │   │       │   ├── PickupRequestRepository.java # PreparedStatements for PICKUP_REQUESTS
    │   │       │   └── FoodAlertRepository.java # PreparedStatements for FOOD_ALERTS
    │   │       ├── exception/
    │   │       │   ├── ValidationException.java  # Bad formats, non-positive numbers
    │   │       │   ├── NotFoundException.java    # Missing records
    │   │       │   └── BusinessRuleException.java# Role violations, overbooking, expired deadlines
    │   │       └── util/
    │   │           ├── InputValidator.java    # Regex email, string, date & quantity validation
    │   │           └── ConsoleInput.java      # Safe Scanner input reading (non-crashing)
    │   └── resources/
    │       ├── application.properties         # Default application properties
    │       └── application.properties.example # Property template
    └── test/
        └── java/
            └── com/foodrescue/
                ├── service/
                │   ├── BusinessRulesAndIntegrityTest.java # 10 comprehensive business logic & transaction tests
                │   └── FoodMatchingServiceTest.java       # Matching algorithm ranking test
                └── util/
                    ├── TestDbHelper.java                  # In-memory H2 schema setup in MySQL mode
                    └── ValidationTest.java                # 7 input & role validator unit tests
```

---

## 3. Required Software

- **Java JDK**: Version 17 (Microsoft OpenJDK 17 or Oracle/Eclipse Temurin 17+).
- **Apache Maven**: Version 3.8+ or 3.9+.
- **MySQL Server**: Version 8.x or 9.x running on `localhost:3306`.

---

## 4. Database Setup and Configuration

### 4.1 Schema Creation in MySQL
Log into MySQL using the command line or MySQL Workbench:

```powershell
# Using MySQL CLI (enter your MySQL root password when prompted):
mysql -u root -p < database/schema.sql
```

Or copy and execute the contents of `database/schema.sql` inside MySQL Workbench.

### 4.2 Credentials Configuration
Create or update your local `.env` file in the root directory:

```properties
DB_HOST=localhost
DB_PORT=3306
DB_NAME=food_rescue
DB_USER=root
DB_PASSWORD=your_actual_mysql_password
DB_URL=jdbc:mysql://localhost:3306/food_rescue?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
```

Configuration resolution order:
1. System Environment Variables (`DB_URL`, `DB_USER`, `DB_PASSWORD`).
2. `.env` file in the project root.
3. `src/main/resources/application.properties`.

---

## 5. How to Compile and Run

### 5.1 Compile and Run Automated Tests
Execute the tests using the bundled wrapper:

```powershell
.\mvnw.cmd clean test
```

Expected output:
```
[INFO] Running com.foodrescue.service.BusinessRulesAndIntegrityTest
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.foodrescue.service.FoodMatchingServiceTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.foodrescue.util.ValidationTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
[INFO] Results:
[INFO] Tests run: 18, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### 5.2 Build Executable Fat JAR
```powershell
.\mvnw.cmd package
```
This builds `target/food-rescue-network-1.0.0.jar` with all dependencies bundled.

### 5.3 Run the Application
You can run the application using either of the following commands:

**Option A (Using launcher script):**
```powershell
.\run.bat
```

**Option B (Using Maven):**
```powershell
.\mvnw.cmd exec:java -Dexec.mainClass="com.foodrescue.Main"
```

**Option C (Using Java directly):**
```powershell
java -jar target/food-rescue-network-1.0.0.jar
```

---

## 6. Menu Options and User Roles

The application features a 16-option interactive console menu:

| Option | Action | Role Permitted | Description |
|:---|:---|:---|:---|
| **1** | Register User | Any | Registers a Provider or Requester with email duplicate checking. |
| **2** | List / View Users | Any | Displays all registered users, roles, and contacts in tabular format. |
| **3** | Select Acting User | Any | Sets the active user for the console session (academic identity selection). |
| **4** | Publish Food Donation | Provider Only | Publishes surplus food with availability window and pickup deadline. |
| **5** | View My Published Donations | Provider Only | Displays all donations published by the current acting provider. |
| **6** | Search Available Food | Any | Parameterized search by category, minimum quantity, or location. |
| **7** | Request Food Pickup | NGO / Volunteer Only | Submits a pickup request for a donation in PENDING status. |
| **8** | View Pickup Requests | Any | Shows incoming requests for providers or submitted requests for requesters. |
| **9** | Approve Pickup Request | Provider Only | Atomically reserves quantity in a transaction and marks request APPROVED. |
| **10**| Reject Pickup Request | Provider Only | Rejects a pending request with an optional reason. |
| **11**| Find Smart Food Matches | NGO / Volunteer | Deterministic ranking based on location, urgency, and portion needs. |
| **12**| View Food Alerts | Any | Displays unread/read alerts with options to mark as read. |
| **13**| Mark Pickup Completed | Provider Only | Completes an approved pickup; marks donation COLLECTED if fully claimed. |
| **14**| Cancel Pickup Request | Requester Only | Cancels request; restores reserved quantity atomically if approved. |
| **15**| Refresh / Run Deadline Checks | Any | Scans database, expires past-deadline items, and generates alerts. |
| **16**| Exit | Any | Gracefully terminates session. |

---

## 7. How to Test All Eight Features Step-by-Step

### Feature 1 — User Registration
1. Choose **1** in the menu.
2. Enter Name: `Bistro Central`, Email: `bistro@central.com`, Contact: `9876543210`.
3. Select Role `1` (`RESTAURANT`). Note the generated User ID (e.g., `#1`).
4. Choose **1** again and register an NGO: `Hope Food Drive`, `hope@ngo.org`, `9123456780`, Role `5` (`NGO`). Note User ID (e.g., `#2`).
5. Choose **2** to verify both users appear.
6. Try registering with `BISTRO@central.com` again; verify duplicate email rejection.

### Feature 2 — Publish Food Donation
1. Ensure acting user is `#1` (Bistro Central). If not, select **3** and enter `1`.
2. Choose **4** to publish a donation:
   - Food Name: `Fresh Vegetable Biryani`
   - Category: `Cooked Meal`
   - Quantity: `50`
   - Pickup Location: `MG Road, Block 4`
   - Available From: Current date/time (e.g. `2026-10-15 12:00`)
   - Pickup Deadline: 4 hours later (e.g. `2026-10-15 16:00`)
3. Observe confirmation. Note generated Donation ID (e.g., `#1`).
4. Select **5** to view your published donations and verify invariants (`Total: 50, Avail: 50, Res: 0, Status: AVAILABLE`).

### Feature 3 — Search Available Food
1. Choose **6**.
2. Filter by Category (e.g., `Cooked`) or leave blank to view all.
3. Observe the tabular listing showing only available, non-expired food.

### Feature 4 — Request Food Pickup
1. Switch acting user to NGO `#2` via option **3**.
2. Choose **7** to request food:
   - Donation ID: `1`
   - Quantity: `30`
   - Pickup Notes: `Can pick up within 2 hours with insulated vehicle`
3. Observe successful submission with status `PENDING`.
4. Switch to `#1` and check option **5**; verify that available quantity remains `50` because pending requests do not reserve inventory until approved.

### Feature 5 — Manage Pickup Requests (Approval & Rejection)
1. Switch to Provider `#1` via option **3**.
2. Choose **8** to view incoming requests; see request from NGO `#2` in `PENDING` status.
3. Choose **9** to approve the request:
   - Request ID: `1`
4. Choose **5** to view donations:
   - Invariant check: `Available: 20`, `Reserved: 30`, `Total: 50`, `Status: AVAILABLE`.
5. Switch to NGO `#2` via option **3**, choose **12** to view Food Alerts; verify receipt of `REQUEST_APPROVED` alert with pickup details.

### Feature 6 — Smart Food Matching
1. Switch to NGO `#2` via option **3**.
2. Choose **11**.
3. Enter desired location (e.g., `MG Road`) and required quantity (e.g., `20`).
4. Observe deterministic ranking:
   - Evaluates location proximity (exact match +35 pts).
   - Evaluates quantity satisfaction (+35 pts).
   - Evaluates urgency golden window (+30 pts for 1-4 hour windows).
   - Shows ranked score (0-100) and clear justification reason.

### Feature 7 — Food Alerts
1. In menu option **12**, view alerts for the current acting user.
2. Observe unread alerts for publication, approval, and approaching deadlines.
3. Select sub-option `1` to mark an individual alert as read or `2` to mark all as read.
4. Verify deduplication: repeated checks will not flood the user with duplicate alerts within 2 hours.

### Feature 8 — Cancel or Complete Pickup
- **Cancellation**:
  1. As NGO `#2`, submit a request for `15` portions on Donation `#1`.
  2. As Provider `#1`, approve the request (`Available: 5`, `Reserved: 45`).
  3. As NGO `#2`, select option **14** and cancel Request `#2`.
  4. As Provider `#1`, view option **5**; verify available quantity restored to `20` and reserved reduced to `30`.
- **Completion**:
  1. As Provider `#1`, select option **13** and complete Request `#1` (`30` portions).
  2. If all portions are collected (`Available: 0` and `Reserved: 0`), verify status automatically transitions to `COLLECTED`.

---

## 8. Important Quantity and Status Rules

1. **Invariants**:
   - `quantity == total_quantity`
   - `available_quantity >= 0` and `reserved_quantity >= 0`
   - `available_quantity + reserved_quantity <= total_quantity`
2. **Pending Requests**:
   - Do NOT deduct available quantity. Requesters may submit requests, but inventory is locked only upon explicit provider approval.
3. **Approval**:
   - Executes under `setAutoCommit(false)` with `SELECT ... FOR UPDATE` row locks.
   - Rejects if requested quantity exceeds current available balance (overbooking prevention).
   - If `available_quantity` drops to 0, status transitions to `RESERVED`.
4. **Cancellation**:
   - If an approved request is cancelled, reserved portions are returned to `available_quantity` provided the donation deadline has not passed.
   - If the deadline has already passed, released food is not made available again.
5. **Completion**:
   - Decrements `reserved_quantity`. When both available and reserved are 0, status transitions to `COLLECTED`.
6. **Deadline Expiration**:
   - When a deadline passes, `available_quantity` is set to 0 and unreserved items transition to `EXPIRED`. Outstanding approved reservations remain intact for auditability and urgent collection.

---

## 9. Known Limitations

1. **Console Notifications vs Push Notifications**:
   - As a standalone console application without a mobile client or push notification gateway (APNs/FCM), notifications are stored in the database (`FOOD_ALERTS`) and retrieved via the menu or polling during session activity.
2. **Identity Selection vs Authentication**:
   - For an academic Java/DBMS console assignment, the acting user is selected via User ID (Menu Option 3). This is an identity-selection mechanism for development and testing, not production-grade token/password authentication.
3. **Distance Calculation**:
   - Location matching uses string and substring matching on location names. Geodesic distance (haversine) is not calculated without GPS coordinate inputs.


## 7. Web frontend (HTML, CSS and JavaScript)

The project now includes a browser frontend in `src/main/resources/web/` and a Java HTTP adapter (`com.foodrescue.WebApplication`) that calls the existing service layer and MySQL repositories. The browser does not connect directly to MySQL.

### Run on Windows

1. Ensure JDK 17 and MySQL are running.
2. In the project root, configure the local `.env` file with your own database password. Copy `.env.example` to `.env` if needed; never commit `.env`.
3. Ensure `database/schema.sql` has been executed in MySQL.
4. Run `run-web.bat` (or `mvnw.cmd compile exec:java -Dexec.mainClass="com.foodrescue.WebApplication"`).
5. Open `http://localhost:8080`.

The web adapter uses `PORT` if set, otherwise port 8080. API endpoints include `/api/health`, `/api/users`, `/api/donations`, `/api/donations/mine`, `/api/matches`, `/api/requests`, `/api/alerts`, and action routes for approval, rejection, cancellation, completion, marking alerts read, and deadline maintenance.

### Important implementation notes

- The original backend is a console application, not an HTTP API. `WebApplication` is the adapter that exposes its existing services over HTTP; it preserves the business rules in those services.
- The account switcher is an acting-user selector for academic/demo use, not secure authentication. Add real authentication and authorization before deploying to the public internet.
- The existing project schema/model should be reviewed for legacy schema compatibility before deployment. This frontend does not bypass the Java services or write to MySQL from the browser.
