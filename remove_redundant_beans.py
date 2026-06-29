import re

with open('/opt/ades/backend-spring/src/main/java/mx/ades/config/HexagonalConfig.java', 'r') as f:
    content = f.read()

# Find @Bean methods that take exactly 1 parameter which is an ApplicationService,
# and whose body is just returning that parameter.
# Pattern matches:
#   @Bean
#   public <ReturnType> <methodName>( <ParamType>ApplicationService <paramName> ) {
#       return <paramName>;
#   }
# This handles optional annotations, spacing, etc.

pattern = re.compile(
    r'\s*@Bean\s*'
    r'(?:@[a-zA-Z\.]+\s*)*'  # optional other annotations like @Primary
    r'public\s+[\w\.]+\s+\w+\(\s*[\w\.]*ApplicationService\s+(\w+)\s*\)\s*'
    r'\{\s*return\s+\1;\s*\}',
    re.MULTILINE
)

# Remove all matches
new_content = pattern.sub('', content)

with open('/opt/ades/backend-spring/src/main/java/mx/ades/config/HexagonalConfig.java', 'w') as f:
    f.write(new_content)

print(f"Removed {len(re.findall(pattern, content))} redundant beans.")
