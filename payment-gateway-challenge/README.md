# Description

Java-based payment gateway with a simulated acquiring bank. Payments can return Authorized, Declined, or Rejected statuses. Merchants can also retrieve details of previous payments by ID.

## Requirements

- JDK 17
- Docker

## Template structure

src/ - A skeleton SpringBoot Application

test/ - Some simple JUnit tests

imposters/ - contains the bank simulator configuration.

docker-compose.yml - configures the bank simulator

## API Documentation

For documentation openAPI is included, and it can be found under the following url: **http://localhost:8090/swagger-ui/index.html**
