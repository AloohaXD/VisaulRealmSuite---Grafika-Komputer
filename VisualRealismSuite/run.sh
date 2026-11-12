#!/usr/bin/env bash
# Jalankan aplikasi (Linux / macOS). Perlu JDK 17+ dan Maven 3.8+.
cd "$(dirname "$0")"
mvn -q compile exec:exec
