#!/bin/bash

# QuantBackEngine - One-Command Startup Script
# Purpose: Start all services, optimize environment, and provide access links.

# Color codes for better visibility
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}==================================================${NC}"
echo -e "${BLUE}   QuantBackEngine 2.0 - Secure Startup          ${NC}"
echo -e "${BLUE}==================================================${NC}"

# 1. Check if Docker is running
if ! docker info >/dev/null 2>&1; then
    echo -e "${RED}Error: Docker is not running. Please start Docker Desktop and try again.${NC}"
    exit 1
fi

# 2. Start services using Docker Compose
echo -e "${YELLOW}Starting services (Database, Backend, Frontend)...${NC}"
docker compose up -d --build

# 3. Wait for services to be ready
echo -ne "${YELLOW}Waiting for API to be ready...${NC}"
MAX_RETRIES=30
COUNT=0
until $(curl -s -f -o /dev/null http://localhost:8080/swagger-ui.html); do
    printf "."
    sleep 2
    COUNT=$((COUNT+1))
    if [ $COUNT -ge $MAX_RETRIES ]; then
        echo -e "\n${RED}Error: Backend failed to start within expected time.${NC}"
        echo -e "${YELLOW}Check logs with: docker compose logs backend${NC}"
        exit 1
    fi
done
echo -e " ${GREEN}Ready!${NC}"

# 4. Final Output
echo -e "${BLUE}==================================================${NC}"
echo -e "${GREEN}SUCCESS: The engine is now running!${NC}"
echo -e ""
echo -e "${BLUE}Dashboard:${NC}    http://localhost:3000"
echo -e "${BLUE}API Docs:${NC}     http://localhost:8080/swagger-ui.html"
echo -e "${BLUE}Database:${NC}     localhost:5432 (PostgreSQL)"
echo -e ""
echo -e "${YELLOW}Security Credentials:${NC}"
echo -e "Username:  ${GREEN}admin${NC} (via Basic Auth for write ops)"
echo -e "Password:  ${GREEN}quantpass123${NC}"
echo -e ""
echo -e "To stop the engine, run: ${YELLOW}docker compose down${NC}"
echo -e "${BLUE}==================================================${NC}"
