# Locale-07 — generate values-{en,uk,pl} + content string keys from translation MD files.
from __future__ import annotations

import html
import re
from pathlib import Path
from xml.sax.saxutils import escape

ROOT = Path(r"F:\SkalackiY_APP")
RU_MD = ROOT / "docs/localization/RU_SOURCE_COPY.md"
TRANS = {
    "en": ROOT / "перевод/EN_TRANSLATION.md",
    "uk": ROOT / "перевод/UK_TRANSLATION.md",
    "pl": ROOT / "перевод/PL_TRANSLATION.md",
}
MAIN_VALUES = ROOT / "android/app/src/main/res/values"
ACCEL = ROOT / "android/app/src/accelerated/res"

ROW_RE = re.compile(
    r"^\| `([^`]+)` \| (.*?) \| (.*?) \| (.*?) \| (.*?) \| (.*?) \|\s*$"
)

CHROME = {
    "ru": {
        "settings_language_section": "Язык",
        "settings_language_error": "Не удалось изменить язык.",
        "language_screen_title": "Выберите язык",
    },
    "uk": {
        "settings_language_section": "Мова",
        "settings_language_error": "Не вдалося змінити мову.",
        "language_screen_title": "Оберіть мову",
    },
    "en": {
        "settings_language_section": "Language",
        "settings_language_error": "Couldn’t change the language.",
        "language_screen_title": "Choose language",
    },
    "pl": {
        "settings_language_section": "Język",
        "settings_language_error": "Nie udało się zmienić języka.",
        "language_screen_title": "Wybierz język",
    },
}

LANGUAGE_OPTIONS = {
    "language_option_ru": "Русский",
    "language_option_uk": "Українська",
    "language_option_en": "English",
    "language_option_pl": "Polski",
}


def parse_rows(path: Path) -> dict[str, str]:
    rows = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        m = ROW_RE.match(line)
        if not m:
            continue
        key, text = m.group(1), m.group(2)
        rows[key] = text
    return rows


def xml_text(s: str) -> str:
    # Android strings: escape XML specials; keep % placeholders as-is.
    # Apostrophe and quotes need escaping in Android resources.
    out = (
        s.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace('"', '\\"')
        .replace("'", "\\'")
    )
    return out


def question_resource_key(key: str) -> str | None:
    # question.1.masculine -> content_question_1
    # question.2.feminine -> content_question_2_feminine
    m = re.fullmatch(r"question\.(\d+)\.(masculine|feminine|neutral)", key)
    if not m:
        return None
    qid, mode = m.group(1), m.group(2)
    if mode == "masculine":
        return f"content_question_{qid}"
    return f"content_question_{qid}_{mode}"


def mood_resource_key(key: str) -> str | None:
    # mood.VERY_LOW.FEMININE.title -> content_mood_very_low_feminine_title
    m = re.fullmatch(
        r"mood\.(VERY_LOW|LOW|NEUTRAL|GOOD|GREAT)\.(FEMININE|MASCULINE|NEUTRAL)\.(title|explanation)",
        key,
    )
    if not m:
        return None
    level, mode, field = m.group(1).lower(), m.group(2).lower(), m.group(3)
    return f"content_mood_{level}_{mode}_{field}"


def pdf_resource_key(key: str) -> str | None:
    mapping = {
        "pdf.archive_title": "pdf_archive_title",
        "pdf.label_question": "pdf_label_question",
        "pdf.label_answer": "pdf_label_answer",
        "pdf.selection_all": "pdf_selection_all",
        "pdf.selection_question_id_only": "pdf_selection_question_id_only",
        "pdf.selection_question_with_text": "pdf_selection_question_with_text",
    }
    return mapping.get(key)


def write_strings_xml(path: Path, strings: dict[str, str], header: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        f"<!-- {header} -->",
        "<resources>",
    ]
    for key in sorted(strings.keys()):
        lines.append(f'    <string name="{key}">{xml_text(strings[key])}</string>')
    lines.append("</resources>")
    lines.append("")
    path.write_text("\n".join(lines), encoding="utf-8")


def write_plurals_xml(path: Path, items: dict[str, str], header: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    order = ["one", "few", "many", "other"]
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        f"<!-- {header} -->",
        "<resources>",
        '    <plurals name="archive_occurrence_deferred_count">',
    ]
    for qty in order:
        if qty in items:
            lines.append(
                f'        <item quantity="{qty}">{xml_text(items[qty])}</item>'
            )
    lines.append("    </plurals>")
    lines.append("</resources>")
    lines.append("")
    path.write_text("\n".join(lines), encoding="utf-8")


def append_content_and_chrome_to_main_ru(ru_rows: dict[str, str]) -> None:
    """Append content_* + chrome keys into main values/strings.xml if missing."""
    strings_path = MAIN_VALUES / "strings.xml"
    text = strings_path.read_text(encoding="utf-8")
    additions: list[tuple[str, str]] = []

    for key, value in ru_rows.items():
        q = question_resource_key(key)
        if q:
            additions.append((q, value))
        m = mood_resource_key(key)
        if m:
            additions.append((m, value))
        p = pdf_resource_key(key)
        if p:
            # PDF templates use ${questionId} — convert to Android %1$s / %1$d style
            if key == "pdf.selection_question_id_only":
                value = "Вопрос %1$d"
            elif key == "pdf.selection_question_with_text":
                value = "Вопрос %1$d: %2$s"
            additions.append((p, value))

    for k, v in CHROME["ru"].items():
        additions.append((k, v))
    for k, v in LANGUAGE_OPTIONS.items():
        additions.append((k, v))

    # Deduplicate preserving order
    seen = set()
    unique = []
    for k, v in additions:
        if k in seen or f'name="{k}"' in text:
            continue
        seen.add(k)
        unique.append((k, v))

    if not unique:
        print("RU main strings: nothing to append")
        return

    block = ["    <!-- Locale-07 content catalogs + language chrome -->"]
    for k, v in unique:
        block.append(f'    <string name="{k}">{xml_text(v)}</string>')
    new_text = text.replace("</resources>", "\n".join(block) + "\n</resources>")
    strings_path.write_text(new_text, encoding="utf-8")
    print(f"RU main strings: appended {len(unique)} keys")


def build_locale_bundle(lang: str, rows: dict[str, str], ru_rows: dict[str, str]) -> tuple[dict[str, str], dict[str, str]]:
    strings: dict[str, str] = {}
    plurals: dict[str, str] = {}

    for key, value in rows.items():
        if key.startswith("archive_occurrence_deferred_count."):
            qty = key.split(".", 1)[1]
            plurals[qty] = value
            continue
        if key == "app_name.accelerated":
            continue  # handled separately
        q = question_resource_key(key)
        if q:
            strings[q] = value
            continue
        m = mood_resource_key(key)
        if m:
            strings[m] = value
            continue
        p = pdf_resource_key(key)
        if p:
            if key == "pdf.selection_question_id_only":
                # Translate word, keep Android placeholder
                # Source templates: "Вопрос ${questionId}" / EN "Question ${questionId}" etc.
                # Replace kotlin placeholders with Android ones
                value = re.sub(r"\$\{questionId\}", "%1$d", value)
                value = re.sub(r"\$questionId\b", "%1$d", value)
            elif key == "pdf.selection_question_with_text":
                value = re.sub(r"\$\{questionId\}", "%1$d", value)
                value = re.sub(r"\$questionId\b", "%1$d", value)
                value = re.sub(r"\$\{questionText\}", "%2$s", value)
                value = re.sub(r"\$questionText\b", "%2$s", value)
            strings[p] = value
            continue
        # Regular UI string key (no dots)
        if "." not in key:
            strings[key] = value

    # Chrome
    strings.update(CHROME[lang])
    strings.update(LANGUAGE_OPTIONS)
    return strings, plurals


def write_accelerated(lang: str, rows: dict[str, str]) -> None:
    name = rows.get("app_name.accelerated")
    if not name:
        return
    if lang == "ru":
        # already in accelerated/values/strings.xml
        return
    path = ACCEL / f"values-{lang}" / "strings.xml"
    write_strings_xml(
        path,
        {"app_name": name},
        f"Locale-07 accelerated app_name ({lang})",
    )


def main() -> None:
    ru = parse_rows(RU_MD)
    print("RU keys", len(ru))
    append_content_and_chrome_to_main_ru(ru)

    for lang, path in TRANS.items():
        rows = parse_rows(path)
        assert len(rows) == 513, (lang, len(rows))
        strings, plurals = build_locale_bundle(lang, rows, ru)
        out_dir = ROOT / f"android/app/src/main/res/values-{lang}"
        write_strings_xml(
            out_dir / "strings.xml",
            strings,
            f"Locale-07 imported {lang.upper()} translations",
        )
        write_plurals_xml(
            out_dir / "plurals.xml",
            plurals,
            f"Locale-07 imported {lang.upper()} plurals",
        )
        write_accelerated(lang, rows)
        print(f"{lang}: strings={len(strings)} plurals={len(plurals)}")


if __name__ == "__main__":
    main()
