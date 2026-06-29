import re

with open('/opt/ades/backend-spring/src/main/java/mx/ades/config/HexagonalConfig.java', 'r') as f:
    content = f.read()

# Match @Bean methods that just return their single parameter (which is a service)
# Pattern: @Bean\s+public\s+[\w\.]+\s+\w+\(\s*[\w\.]+\s+(\w+)\s*\)\s*\{\s*return\s+\1;\s*\}
pattern = re.compile(r'\s*@Bean\s+public\s+[\w\.]+\s+\w+\(\s*[\w\.]+\s+(\w+)\s*\)\s*\{\s*return\s+\1;\s*\}')

new_content = pattern.sub('', content)

with open('/opt/ades/backend-spring/src/main/java/mx/ades/config/HexagonalConfig.java', 'w') as f:
    f.write(new_content)

print("Done")
