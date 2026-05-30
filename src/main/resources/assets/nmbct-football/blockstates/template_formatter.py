import json

COMMENT_KEY = "_comment"
REPLACEMENTS_KEY = "_replacements"
ALIAS_PLACEHOLDER_SPLITTER = "|"
PLACEHOLDER_START = "$"
TEMPLATE_FILE_NAME_PREFIX = "template$"


def format_template(template, replacements: dict):
    """
    :param template: 待格式化的模板，dict/list会递归格式化，str会格式化后返回，非dict/list类型的值会被原样保留
    :param replacements: 替换占位符的字典，键为占位符名称（不带 $ 前缀），值为替换后的值；如果模板中存在占位符但在 replacements 中没有对应的键，则会发出警告并忽略该占位符
    :return: 格式化后的模板
    """

    if isinstance(template, dict):
        # 删除注释
        comment_keys = [key for key in template if key.startswith(COMMENT_KEY)]
        for comment_key in comment_keys:
            del template[comment_key]
        # 把字符串中的占位符替换掉
        replaced = {}
        for key, value in template.items():
            if isinstance(value, str) and value.startswith(PLACEHOLDER_START):
                placeholder = value[len(PLACEHOLDER_START):]
                if placeholder in replacements:
                    replaced[key] = replacements[placeholder]
                else:
                    print(f"Warning: No replacement found for placeholder '{placeholder}', ignored.")
            elif isinstance(value, dict) or isinstance(value, list):
                replaced[key] = format_template(value, replacements)
            else:
                replaced[key] = value
        return replaced
    elif isinstance(template, list):
        return [format_template(item, replacements) for item in template]
    elif isinstance(template, str):
        if template.startswith(PLACEHOLDER_START):
            placeholder = template[len(PLACEHOLDER_START):]
            if placeholder in replacements:
                return replacements[placeholder]
            else:
                print(f"Warning: No replacement found for placeholder '{placeholder}', ignored.")
                return template
        else:
            return template
    else:
        return template


def extract_replacements_and_remove(template: dict) -> dict:
    """
    从模板中提取替换项并删除 _replacements 键
    :param template: 包含 _replacements 键的模板字典
    :return: 提取出的替换项字典，如果模板中没有 _replacements 键则返回一个空字典
    """
    if REPLACEMENTS_KEY in template:
        raw_replacements = template[REPLACEMENTS_KEY]
        if not isinstance(raw_replacements, dict):
            print(f"Warning: The value of '{REPLACEMENTS_KEY}' should be a dictionary, but got {type(raw_replacements).__name__}. Ignored.")
            return {}
        del template[REPLACEMENTS_KEY]
        replacements = {}
        for key, value in raw_replacements.items():
            if key.startswith(COMMENT_KEY):
                continue
            aliases = key.split(ALIAS_PLACEHOLDER_SPLITTER)
            for alias in aliases:
                if alias in replacements:
                    print(f"Warning: Duplicate placeholder '{alias}' found in replacements, ignored.")
                else:
                    replacements[alias] = value
        return replacements
    else:
        return {}


def format_template_from_file(template_path: str) -> dict | list:
    """
    从 JSON 文件中读取模板并进行格式化
    :param template_path: 模板文件的路径，必须是一个 JSON 文件
    :return: 格式化后的模板，类型与文件中的 JSON 数据相同；如果文件中包含 _replacements 键，则会使用其中的替换项进行格式化；如果文件中没有 _replacements 键，则会直接格式化模板而不进行替换
    """
    with open(template_path, "r", encoding="utf-8") as f:
        template = json.load(f)
    replacements = extract_replacements_and_remove(template)
    return format_template(template, replacements)


def save_formatted_template(formatted_template: dict | list, template_path: str):
    output_path = template_path.replace(TEMPLATE_FILE_NAME_PREFIX, "")
    if output_path == template_path:
        output_path = template_path + "_formatted"
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(formatted_template, f, indent=4, ensure_ascii=False)
    print(f"Formatted template saved to '{output_path}'.")


if __name__ == '__main__':
    template_path = input("Enter the path of the template JSON file: ")
    formatted_template = format_template_from_file(template_path)
    save_formatted_template(formatted_template, template_path)

