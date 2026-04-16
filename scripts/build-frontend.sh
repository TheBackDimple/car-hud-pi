#!/bin/bash
# Build React frontend for production
set -e
cd "$(dirname "$0")/../frontend"
npm run build
