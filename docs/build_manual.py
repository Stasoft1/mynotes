"""Собирает docs/Руководство.md: общие главы + тексты справки из app/src/main/assets/help (те же тексты показываются в приложении)."""
import re, pathlib
root = pathlib.Path(__file__).resolve().parent.parent
H = root / "app/src/main/assets/help"

def help_text(name, level):
    """Текст справки без заголовка первого уровня; заголовки '## ' опускаются на level уровней."""
    out = []
    for l in (H / (name + ".md")).read_text(encoding="utf-8").splitlines():
        if l.startswith("# "):
            continue
        m = re.match(r"^(#{2,3}) (.*)", l)
        out.append("#" * (len(m.group(1)) + level) + " " + m.group(2) if m else l)
    return "\n".join(out).strip() + "\n"

def lead(name):
    """Первая строка справки с '**Для чего.**' для краткого оглавления."""
    t = (H / (name + ".md")).read_text(encoding="utf-8")
    m = re.search(r"\*\*Для чего\.\*\* (.*)", t)
    return m.group(1) if m else ""

def title(name):
    return re.search(r"^# (.*)", (H / (name + ".md")).read_text(encoding="utf-8"), re.M).group(1)

GROUPS = [
    ("Деньги и быт", ["budget", "subs", "debt", "util"]),
    ("Дом и семья", ["bday", "warr"]),
    ("Здоровье и привычки", ["health", "habits"]),
    ("Транспорт", ["auto"]),
    ("Время и место", ["day", "geo", "route"]),
    ("Голос и ИИ", ["giga"]),
]

parts = []
parts.append((root / "docs/manual_head.md").read_text(encoding="utf-8").rstrip() + "\n")
parts.append("## 5. Шаблоны\n\n" + help_text("templates", 1))
parts.append("## 6. Переменные и подсчёты\n\n" + help_text("vars", 1))
parts.append((root / "docs/manual_mid.md").read_text(encoding="utf-8").rstrip() + "\n")
parts.append("## 10. Голосовой ввод\n\n" + help_text("voice", 1))
parts.append((root / "docs/manual_core2.md").read_text(encoding="utf-8").rstrip() + "\n")
mod = ["## 15. Модули\n",
       "Модули — необязательные функции. Включаются в Настройки → «Модули». Выключенный модуль нигде не виден: ни в меню, ни в настройках, ни в редакторе; ваши данные при этом не удаляются. "
       "Справка по каждому модулю есть и в самом приложении: кнопка «Как пользоваться» в списке модулей, кнопка «?» в окне модуля и раздел «Справка» в Настройках.\n",
       "Быстрый выбор:\n"]
for g, ids in GROUPS:
    for i in ids:
        mod.append(f"- **{title(i)}** — {lead(i)}")
mod.append("")
n = 1
for g, ids in GROUPS:
    mod.append(f"### {g}\n")
    for i in ids:
        mod.append(f"#### 15.{n}. {title(i)}\n")
        mod.append(help_text(i, 3))
        n += 1
parts.append("\n".join(mod))
parts.append((root / "docs/manual_tail.md").read_text(encoding="utf-8").rstrip() + "\n")
(root / "docs/Руководство.md").write_text("\n".join(p.rstrip() + "\n" for p in parts), encoding="utf-8")
