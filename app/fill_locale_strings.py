from pathlib import Path
import xml.etree.ElementTree as ET

base_path = Path('app/src/main/res/values/strings.xml')
if not base_path.exists():
    raise FileNotFoundError(base_path)

base_tree = ET.parse(base_path)
base_root = base_tree.getroot()
base_strings = []
for child in base_root:
    if child.tag != 'string':
        continue
    name = child.attrib.get('name')
    if not name:
        continue
    text = child.text or ''
    attrib = dict(child.attrib)
    base_strings.append((name, text, attrib))

for locale_file in sorted(Path('app/src/main/res').glob('values-*/strings.xml')):
    tree = ET.parse(locale_file)
    root = tree.getroot()
    current = {child.attrib['name']: (child.text or '', dict(child.attrib)) for child in root if child.tag == 'string' and 'name' in child.attrib}
    new_root = ET.Element('resources')
    for name, base_text, attrib in base_strings:
        if name in current:
            text, existing_attrib = current[name]
            elem_attrib = existing_attrib.copy()
            elem = ET.SubElement(new_root, 'string', {k: v for k, v in elem_attrib.items() if k != 'text'})
            elem.text = text
        else:
            elem = ET.SubElement(new_root, 'string', {k: v for k, v in attrib.items() if k != 'text'})
            elem.text = base_text
    
    def indent(elem, level=0):
        i = '    ' * level
        if len(elem):
            if not elem.text or not elem.text.strip():
                elem.text = '\n' + '    ' * (level + 1)
            for child in elem:
                indent(child, level + 1)
            if not child.tail or not child.tail.strip():
                child.tail = '\n' + i
        if level and (not elem.tail or not elem.tail.strip()):
            elem.tail = '\n' + i
    indent(new_root)
    ET.ElementTree(new_root).write(locale_file, encoding='utf-8', xml_declaration=True)
    print(f'Updated {locale_file}')
