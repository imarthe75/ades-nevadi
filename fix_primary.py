import re

with open('/opt/ades/backend-spring/src/main/java/mx/ades/config/HexagonalConfig.java', 'r') as f:
    content = f.read()

# We want to find:
# @Bean
# public <Type>ApplicationService <name>ApplicationService(...)
# and replace it with:
# @Bean
# @org.springframework.context.annotation.Primary
# public <Type>ApplicationService <name>ApplicationService(...)

pattern = re.compile(r'(\s*@Bean\s*\n\s*)(public\s+[\w\.]*ApplicationService\s+\w+ApplicationService\s*\()')
new_content = pattern.sub(r'\1@org.springframework.context.annotation.Primary\n    \2', content)

with open('/opt/ades/backend-spring/src/main/java/mx/ades/config/HexagonalConfig.java', 'w') as f:
    f.write(new_content)

print("Done")
