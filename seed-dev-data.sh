#!/bin/bash

# Default values
API_URL=${1:-"http://localhost:8080"}

echo "Seeding development data for $API_URL..."

# Call the seed endpoint
RESPONSE=$(curl -s -X POST "$API_URL/dev/seed")

if echo "$RESPONSE" | grep -q "Seed successful"; then
    echo "Successfully seeded development data!"
    echo "Login: dev@example.com / password123"
    echo "$RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$RESPONSE"
else
    echo "Failed to seed data. Is the server running in development mode?"
    echo "Response: $RESPONSE"
    exit 1
fi
