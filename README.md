# ⭕ The Circle

> **Master's Thesis Project (TFM)**: A collaborative circular economy web platform focused on solidarity buying, selling, and renting.

## 📖 About the Project

**The Circle** is a collaborative web application designed to facilitate the buying, selling, and renting of products and services at symbolic or reduced prices, with a strong social focus.

While it operates similarly to existing collaborative economy platforms, its core differentiator is the **prioritization of low-income individuals and those at risk of social exclusion**. The platform allows users to publish both offers (sales, rentals, and free transfers) and demands for products or services.

To ensure ethical use and a secure environment—especially protecting those who lend or donate goods—the platform integrates official identity document verification and automatic generation of digital contracts for transactions.

---

## 🎯 Objectives & Impact

### Main Objective
To develop a functional, secure, and scalable software solution that promotes the reuse of goods and ensures equitable access to basic resources.

### Specific Objectives
* **Modern Architecture:** Design a robust web architecture using state-of-the-art technologies studied during the Master's program.
* **Identity Verification:** Implement a secure user management system featuring official ID validation.
* **Digital Contracts:** Develop a system to automatically generate digital contracts for rentals and sales to ensure user safety and trust.
* **Smart Valuation:** Incorporate an automatic estimation mechanism to suggest the fair value of products.
* **Gamification & Incentives:** Implement a reward system (gifts, raffles, discounts) to encourage solidarity among users.

### Project Impact
* **Social:** Facilitates access to essential goods and services for vulnerable communities.
* **Technological:** Applies principles of responsible, sustainable, and ethical software engineering.

---

## ✨ Key Features

* **Offer Publishing:** List items or services for sale, rent, or free donation.
* **Demand Publishing:** Users in need can post specific requests for goods or services.
* **ID Verification:** Secure onboarding process to validate user identities.
* **Automated Contracting:** Generates binding digital agreements for transactions.
* **User Reward System:** Incentives for active, generous community members.

---

## 🛠️ Tech Stack & Architecture

| Component | Technology | Description |
| :--- | :--- | :--- |
| **Frontend** | React + Vite | User interface built with JavaScript, HTML, and CSS. |
| **Backend** | Java | Core server-side logic and API. |
| **Database** | SQL Database | Relational DBMS (PostgreSQL, MySQL, or similar). |
| **Search Engine** | Elasticsearch | For fast, scalable search and analytics of offers/demands. |
| **Document Mgmt.** | *TBD* | Tool for generating and storing digital contracts. |
| **ID Validation** | Custom / API | Integration with a 3rd-party service or a simplified custom implementation. |
| **Version Control**| Git / GitHub | Code tracking and collaborative group development. |

### Microservices Ecosystem
* `api-gateway`: Entry point and routing for all client requests.
* `ms-users`: Identity management, profiles, authentication, and KYC flow.
* `ms-catalog`: Offers and demands CRUD, integrated with OpenSearch.
* `ms-contracts`: Automatic PDF generation and OTP signature flows.

---

## 🔄 Methodology

This group Master's Thesis is developed using an **incremental and iterative methodology** inspired by Agile principles. This approach allows for continuous integration, regular feedback, and adaptive planning, making it perfectly suited for collaborative academic development.

---

## 🚀 Setup & Installation

### Important Prerequisite (Linux / WSL2 users)
OpenSearch requires a high virtual memory map limit. If the `opensearch` container crashes on startup with a `max virtual memory areas vm.max_map_count [65530] is too low` error, you must increase this limit on your host machine:
* **Linux:** Run `sudo sysctl -w vm.max_map_count=262144`
* **Windows (WSL2):** Open PowerShell, run `wsl -d docker-desktop`, then run the `sysctl` command above.

1. Clone the repository:
   ```bash
   git clone [https://github.com/your-username/the-circle.git](https://github.com/your-username/the-circle.git)
   cd the-circle
   ```

2. Create a `.env` file in the project root before starting Docker Compose. At minimum, define the environment variables required by the services:
   ```env
   PGADMIN_DEFAULT_EMAIL=admin@example.com
   PGADMIN_DEFAULT_PASSWORD=change-me
   DB_USER=thecircle
   DB_PASSWORD=change-me
   JWT_SECRET=replace-with-a-long-random-secret
   USERS_INTERNAL_KEY=replace-with-a-shared-internal-key
   CATALOG_INTERNAL_KEY=replace-with-a-shared-internal-key
   CONTRACTS_INTERNAL_KEY=replace-with-a-shared-internal-key
   ```

   Then start the containers:
   ```bash
   docker compose up -d
   ```
   
3. Install frontend dependencies:
    ```bash
    cd frontend
    npm install
    npm run dev
    ```
   
3. Contributors:
    - [MavapeGZ](https://github.com/MavapeGZ)
    - [grodriguez1722](https://github.com/grodriguez1722)

## Account deletion policy

When a user deletes their account, ms-users anonymizes the profile, revokes sessions, deletes their published articles, and asks ms-contracts to remove that user's open owner-side contracts together with their payments and generated PDF/signature artifacts.

Completed and cancelled contracts are kept as historical records. Receiver-side contracts are not removed by this flow, because they belong to the other party's product lifecycle.
