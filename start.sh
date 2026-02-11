#!/bin/bash

# QuantBackEngine Startup Script
# Usage: ./start.sh

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}=== QuantBackEngine Startup ===${NC}"

# Check for Docker
if ! command -v docker &> /dev/null; then
    echo -e "${RED}Error: Docker is not installed or not in PATH.${NC}"
    exit 1
fi

# Check for Docker Compose
if ! command -v docker-compose &> /dev/null; then
    # Try 'docker compose' (V2)
    if ! docker compose version &> /dev/null; then
         echo -e "${RED}Error: Docker Compose is not installed.${NC}"
         exit 1
    else
        DOCKER_COMPOSE_CMD="docker compose"
    fi
else
    DOCKER_COMPOSE_CMD="docker-compose"
fi

echo -e "${GREEN}Using ${DOCKER_COMPOSE_CMD}...${NC}"

# Stop existing containers
echo -e "${BLUE}Stopping any running containers...${NC}"
$DOCKER_COMPOSE_CMD down

# Build and Start
echo -e "${BLUE}Building and starting services...${NC}"
$DOCKER_COMPOSE_CMD up -d --build

if [ $? -eq 0 ]; then
    echo -e "${GREEN}=== Startup Successful ===${NC}"
    echo -e "Frontend: ${BLUE}http://localhost:3000${NC}"
    echo -e "Backend:  ${BLUE}http://localhost:8080/swagger-ui.html${NC}"
    echo -e "Database: ${BLUE}localhost:5432${NC}"
    echo -e "Use './stop.sh' to stop the application."
else
    echo -e "${RED}Error: Startup failed. Check logs with 'docker-compose logs -f'${NC}"
    exit 1
fi
