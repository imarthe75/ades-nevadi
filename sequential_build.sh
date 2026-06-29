#!/bin/bash
# Script to sequentially pull, build, and start Docker Compose services
# By doing it sequentially and pruning builder cache, we avoid disk space limits
# By retrying pulls, we bypass network timeouts

services=$(docker compose config --services)

for service in $services; do
  echo "========================================="
  echo "Processing service: $service"
  echo "========================================="
  
  # Try to pull images (up to 5 retries for network timeouts)
  for i in {1..5}; do
    if docker compose pull $service; then
      echo "Successfully pulled $service"
      break
    else
      echo "Failed to pull $service. Retrying ($i/5)..."
      sleep 5
    fi
  done
  
  # Start and build the service
  echo "Building and starting $service..."
  if ! docker compose up -d --build $service; then
    echo "ERROR: Failed to build/start $service"
  fi
  
  # Clean up builder cache to avoid 'No space left on device'
  echo "Cleaning up builder cache..."
  docker builder prune -a -f
  
done

echo "All services processed."
