#!/bin/bash
ROOT_DIR="$(dirname "$0")/../.."

if [ -f "$ROOT_DIR/.env" ]; then
  source "$ROOT_DIR/.env"
fi

SPRING_PROFILES_ACTIVE=migration \
  FIRESTORE_EMULATOR_HOST="${FIRESTORE_EMULATOR_HOST:-localhost:8079}" \
  FIREBASE_PROJECT_ID="${FIREBASE_PROJECT_ID}" \
  SPRING_DATASOURCE_URL="${MIGRATION_DATASOURCE_URL:-jdbc:postgresql://localhost:5434/university-booking}" \
  SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-postgres}" \
  SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD}" \
  SPRING_DATA_REDIS_HOST="${SPRING_DATA_REDIS_HOST:-localhost}" \
  java -jar "$ROOT_DIR/backend/build/libs/university-booking-0.0.1-SNAPSHOT.jar"
