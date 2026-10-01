from pathlib import Path
import re

ids = [
    "todoOnOff",
    "remindersOnOff",
    "mindfulMorningOnOff",
    "hourlyChimeOnOff",
    "screenTimeOnOff",
    "homeAppIcons",
    "dailyWallpaper",
    "statusBar",
    "focusModeNotificationsLock",
    "focusModeHideStatusBar",
    "weatherOnOff",
    "prayerOnOff",
]

pattern = re.compile(
    r"[ \t]*<TextView\s+android:id=\"@\+id/(" + "|".join(ids) + r")\"[\s\S]*?/>",
    re.M,
)


def switch_xml(indent: str, vid: str, gravity: str | None) -> str:
    g = f'\n{indent}    android:layout_gravity="{gravity}"' if gravity else ""
    return (
        f"{indent}<androidx.appcompat.widget.SwitchCompat\n"
        f'{indent}    android:id="@+id/{vid}"\n'
        f'{indent}    style="@style/SettingsOnOffSwitch"\n'
        f'{indent}    android:layout_width="wrap_content"\n'
        f'{indent}    android:layout_height="wrap_content"{g} />'
    )


def repl(m: re.Match[str]) -> str:
    block = m.group(0)
    vid = m.group(1)
    indent = re.match(r"[ \t]*", block).group(0)
    grav = None
    gm = re.search(r'android:layout_gravity="([^"]+)"', block)
    if gm:
        grav = gm.group(1).replace("end|bottom", "end|center_vertical")
    return switch_xml(indent, vid, grav)


for p in [
    Path(r"d:\sukun\app\src\main\res\layout\fragment_settings.xml"),
    Path(r"d:\sukun\app\src\main\res\layout-land\fragment_settings.xml"),
]:
    text = p.read_text(encoding="utf-8")
    new, n = pattern.subn(repl, text)
    print(p.name, "replacements", n)
    p.write_text(new, encoding="utf-8")
