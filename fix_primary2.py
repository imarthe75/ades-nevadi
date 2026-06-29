import re

with open('/opt/ades/backend-spring/src/main/java/mx/ades/config/HexagonalConfig.java', 'r') as f:
    lines = f.readlines()

new_lines = []
i = 0
while i < len(lines):
    line = lines[i]
    if "@Bean" in line:
        # check next 1-2 lines for ApplicationService method declaration
        is_app_service = False
        method_name = ""
        for j in range(1, 3):
            if i + j < len(lines):
                if "ApplicationService" in lines[i+j] and "public " in lines[i+j]:
                    # check if this is the ApplicationService bean itself, not an interface taking it
                    if lines[i+j].strip().startswith("public mx.ades.") and "UseCase " not in lines[i+j]:
                        is_app_service = True
        
        if is_app_service:
            new_lines.append(line)
            new_lines.append("    @org.springframework.context.annotation.Primary\n")
            i += 1
            continue

    new_lines.append(line)
    i += 1

with open('/opt/ades/backend-spring/src/main/java/mx/ades/config/HexagonalConfig.java', 'w') as f:
    f.writelines(new_lines)

print("Done")
