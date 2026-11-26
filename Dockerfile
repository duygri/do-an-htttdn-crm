FROM node:22-bookworm-slim AS web
WORKDIR /web
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
ENV VITE_SAME_ORIGIN=true
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS java
WORKDIR /build
COPY backend/pom.xml ./pom.xml
RUN mvn -B dependency:go-offline
COPY backend/src/main/java ./src/main/java
# Deliberately never copy local application.yml or local environment files.
COPY deploy/application.yml ./src/main/resources/application.yml
# Idempotent migrations applied by spring.sql.init before Hibernate validates the schema.
COPY database/migration_supplier_receipts_invoices.sql ./src/main/resources/db/migration_supplier_receipts_invoices.sql
COPY database/migration_inventory_suppliers_loyalty.sql ./src/main/resources/db/migration_inventory_suppliers_loyalty.sql
COPY database/migration_shipping_returns_refunds.sql ./src/main/resources/db/migration_shipping_returns_refunds.sql
COPY database/migration_loyalty_idempotency.sql ./src/main/resources/db/migration_loyalty_idempotency.sql
COPY database/migration_email_outbox.sql ./src/main/resources/db/migration_email_outbox.sql
COPY database/migration_support_experience.sql ./src/main/resources/db/migration_support_experience.sql
COPY database/migration_attachment_metadata.sql ./src/main/resources/db/migration_attachment_metadata.sql
COPY database/migration_product_variants.sql ./src/main/resources/db/migration_product_variants.sql
COPY --from=web /web/dist ./src/main/resources/static
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --system crm && useradd --system --gid crm crm
COPY --from=java --chown=crm:crm /build/target/crm-backend-0.0.1-SNAPSHOT.jar /app/app.jar
USER crm
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -XX:+ExitOnOutOfMemoryError"
EXPOSE 10000
CMD ["java", "-jar", "/app/app.jar"]
