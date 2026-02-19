#!/bin/bash
# ============================================
# fix_node.sh — Fix Node.js for macOS 12
# ============================================
# Problem: Node.js v25 is incompatible with macOS Monterey (12.x).
#          It crashes with "Symbol not found: __ZNSt3__122__libcpp_verbose_abortEPKcz"
#          because v25 requires libc++ from macOS 13+.
#
# Fix:     Switch to Node.js v20 LTS and remove the hardcoded v25 path from .zshrc.
# ============================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo ""
echo -e "${YELLOW}🔧 Fixing Node.js for macOS 12 compatibility...${NC}"
echo ""

# --- Step 1: Load NVM ---
export NVM_DIR="$HOME/.nvm"
[ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh"

# --- Step 2: Remove hardcoded v25 path from .zshrc ---
ZSHRC="$HOME/.zshrc"
if grep -q "v25.5.0" "$ZSHRC" 2>/dev/null; then
    echo -e "${YELLOW}→ Removing hardcoded Node v25 path from ~/.zshrc...${NC}"
    # Remove the line containing the hardcoded v25 PATH
    sed -i '' '/v25\.5\.0/d' "$ZSHRC"
    echo -e "${GREEN}  ✓ Removed hardcoded v25 path${NC}"
else
    echo -e "${GREEN}  ✓ No hardcoded v25 path found in ~/.zshrc${NC}"
fi

# --- Step 3: Set NVM default to v20 LTS ---
echo -e "${YELLOW}→ Setting Node.js v20 (LTS) as default...${NC}"
nvm alias default 20
nvm use 20
echo -e "${GREEN}  ✓ Default set to $(node --version)${NC}"

# --- Step 4: Verify npx works ---
echo ""
echo -e "${YELLOW}→ Verifying npx works...${NC}"
NPX_VERSION=$(npx --version 2>&1)
if [ $? -eq 0 ]; then
    echo -e "${GREEN}  ✓ npx v${NPX_VERSION} is working!${NC}"
else
    echo -e "${RED}  ✗ npx still not working. Error: ${NPX_VERSION}${NC}"
    exit 1
fi

# --- Done ---
echo ""
echo -e "${GREEN}✅ Fix complete!${NC}"
echo ""
echo "  Node: $(node --version)"
echo "  npm:  $(npm --version)"
echo "  npx:  $(npx --version)"
echo ""
echo -e "${YELLOW}⚠️  Please restart your terminal (or any app using npx) for changes to take full effect.${NC}"
echo ""
