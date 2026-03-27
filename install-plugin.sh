#!/bin/bash
#
# Install the nf-crukci-logscan plugin for local testing
#

set -e

PLUGIN_ID="nf-crukci-logscan"
PLUGIN_VERSION="1.0.0-SNAPSHOT"
PLUGIN_DIR="$HOME/.nextflow/plugins/${PLUGIN_ID}-${PLUGIN_VERSION}"
JAR_FILE="target/${PLUGIN_ID}-${PLUGIN_VERSION}.jar"

# Check if JAR exists
if [ ! -f "$JAR_FILE" ]; then
    echo "Error: JAR file not found: $JAR_FILE"
    echo "Run 'mvn clean package' first"
    exit 1
fi

# Clean up any existing installation
if [ -d "$PLUGIN_DIR" ]; then
    echo "Removing existing plugin installation..."
    rm -rf "$PLUGIN_DIR"
fi

# Create plugin directory structure (PF4J expects lib/ subdirectory)
echo "Creating plugin directory: $PLUGIN_DIR"
mkdir -p "$PLUGIN_DIR/lib"
mkdir -p "$PLUGIN_DIR/META-INF"

# Copy JAR to lib subdirectory
echo "Copying JAR to $PLUGIN_DIR/lib/"
cp "$JAR_FILE" "$PLUGIN_DIR/lib/"

# Extract MANIFEST.MF to plugin root (required by PF4J)
echo "Extracting manifest..."
unzip -j "$JAR_FILE" META-INF/MANIFEST.MF -d "$PLUGIN_DIR/META-INF/" > /dev/null 2>&1

echo ""
echo "✓ Plugin installed successfully to: $PLUGIN_DIR"
echo ""
echo "Contents:"
ls -lh "$PLUGIN_DIR"
ls -lh "$PLUGIN_DIR/lib"
echo ""
echo "You can now test with:"
echo "  cd examples && nextflow run example-pipeline.nf -c example.config"
