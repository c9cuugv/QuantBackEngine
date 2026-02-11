#!/bin/bash

# QuantBackEngine Shutdown Script
# Usage: ./stop.sh

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}=== QuantBackEngine Shutdown ===${NC}"

# Determine command
if command -v docker-compose &> /dev/null; then
    DOCKER_COMPOSE_CMD="docker-compose"
elif docker compose version &> /dev/null; then
    DOCKER_COMPOSE_CMD="docker compose"
else
    echo "Docker Compose not found."
    exit 1
fi

echo -e "${BLUE}Stopping containers...${NC}"
$DOCKER_COMPOSE_CMD down

echo -e "${GREEN}Application stopped.${NC}"
