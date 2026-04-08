#!/bin/bash
#
# Install the nf-crukci-support plugin for local testing
#

set -e

PLUGIN_ID="nf-crukci-support"
PLUGIN_VERSION="1.0.0"
PLUGIN_DIR="$HOME/.nextflow/plugins/${PLUGIN_ID}-${PLUGIN_VERSION}"
ZIP_FILE="target/${PLUGIN_ID}-${PLUGIN_VERSION}.zip"

# Check if zip exists
if [ ! -f "$ZIP_FILE" ]; then
    echo "Error: zip file not found: $ZIP_FILE"
    echo "Run 'mvn clean package' first"
    exit 1
fi

# Clean up any existing installation
if [ -d "$PLUGIN_DIR" ]; then
    echo "Removing existing plugin installation..."
    rm -rf "$PLUGIN_DIR"
fi

# Create plugin directory structure (PF4J expects lib/ subdirectory)
mkdir -p "$PLUGIN_DIR"

# Unzip zip file into plugin directory.
unzip -q -d "$PLUGIN_DIR" "$ZIP_FILE"

echo ""
echo "✓ Plugin installed successfully to: $PLUGIN_DIR"
echo ""
