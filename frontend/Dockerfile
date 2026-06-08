# ============================================================
# Stage 1: Install dependencies (cached unless package.json changes)
# ============================================================
FROM node:20-alpine AS deps
WORKDIR /app

# Copy only package files first for optimal caching
COPY package.json package-lock.json ./

# Install dependencies (cached layer)
RUN npm ci --prefer-offline --no-audit --no-fund

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
# Stage 3: Minimal runtime image (standalone — no node_modules needed)
# ============================================================
FROM node:20-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Standalone bundle includes only what's needed to run
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
COPY --from=build /app/public ./public

RUN chown -R appuser:appgroup /app
USER appuser

EXPOSE 3000

ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV PORT=3000
ENV HOSTNAME=0.0.0.0

CMD ["node", "server.js"]
