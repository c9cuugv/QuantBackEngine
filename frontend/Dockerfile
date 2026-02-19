# ============================================================
# Stage 1: Install dependencies (cached unless package.json changes)
# ============================================================
FROM node:20-alpine AS deps
WORKDIR /app

# Copy only package files first for optimal caching
COPY package.json package-lock.json ./

# Install dependencies (cached layer)
RUN npm ci --prefer-offline

# ============================================================
# Stage 2: Build the application
# ============================================================
FROM deps AS build
WORKDIR /app

# Copy source code
COPY . .

# Set the API URL for the build
ARG NEXT_PUBLIC_API_URL=http://backend:8080
ENV NEXT_PUBLIC_API_URL=${NEXT_PUBLIC_API_URL}

# Build Next.js (standalone output for smaller image)
RUN npm run build

# ============================================================
# Stage 3: Minimal runtime image
# ============================================================
FROM node:20-alpine
WORKDIR /app

# Create non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy only what's needed to run
COPY --from=build /app/.next ./.next
COPY --from=build /app/node_modules ./node_modules
COPY --from=build /app/package.json ./package.json

# Create public dir if it exists (optional assets)
# COPY --from=build /app/public ./public 2>/dev/null || true

# Set ownership
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 3000

# Environment variables
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1

# Run
CMD ["npm", "start"]
