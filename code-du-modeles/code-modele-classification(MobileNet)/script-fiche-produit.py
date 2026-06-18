import os
from reportlab.pdfgen import canvas as rl_canvas
from reportlab.lib.pagesizes import A4
from reportlab.lib import colors
from reportlab.lib.units import mm
from reportlab.platypus import Paragraph, Table, TableStyle
from reportlab.lib.styles import ParagraphStyle

# Création du dossier de destination
os.makedirs("output", exist_ok=True)
OUTPUT_PDF = "output/AgroScan_IA_OnePager.pdf"

# Fonction pour garantir la présence d'un logo de test si logo.png est absent
def ensure_logo():
    if not os.path.exists("logo.png"):
        from PIL import Image, ImageDraw
        img = Image.new("RGBA", (300, 300), (255, 255, 255, 0))
        draw = ImageDraw.Draw(img)
        draw.ellipse([10, 10, 290, 290], fill=(255, 255, 255, 40))
        draw.ellipse([30, 30, 270, 270], fill=(46, 125, 50, 255))
        draw.ellipse([45, 45, 255, 255], fill=(27, 94, 32, 255))
        draw.chord([80, 80, 220, 220], start=0, end=90, fill=(255, 255, 255, 240))
        draw.chord([80, 80, 220, 220], start=180, end=270, fill=(165, 214, 167, 240))
        for x in [130, 150, 170]:
            for y in [130, 150, 170]:
                draw.ellipse([x-3, y-3, x+3, y+3], fill=(255, 255, 255, 200))
        img.save("logo.png")

ensure_logo()

W, H = A4

# ── Palette de Couleurs Professionnelle ──────────────────
VERT_PRIMARY  = colors.HexColor("#1B5E20") 
VERT_ACCENT   = colors.HexColor("#2E7D32") 
VERT_LIGHT    = colors.HexColor("#E8F5E9") 
VERT_MUTED    = colors.HexColor("#A5D6A7") 
ANTHRACITE    = colors.HexColor("#263238") 
GRIS_TEXT     = colors.HexColor("#546E7A") 
BOND_BG       = colors.HexColor("#FAFAFA") 
BLANC         = colors.white

c = rl_canvas.Canvas(OUTPUT_PDF, pagesize=A4)
c.setFillColor(BOND_BG)
c.rect(0, 0, W, H, fill=1, stroke=0)

# ── Styles Globaux Reutilisables ────────────────────────
style_body = ParagraphStyle(
    'BodyText',
    fontName='Helvetica',
    fontSize=8,
    leading=11.5,
    textColor=ANTHRACITE
)

style_case_title = ParagraphStyle(
    'CaseTitle',
    fontName='Helvetica-Bold',
    fontSize=8.5,
    leading=11,
    textColor=VERT_PRIMARY
)

# ── Fonctions de Dessin Modulaires ───────────────────────
def draw_section_header(canvas, label, x, y, width):
    height = 13
    canvas.setFillColor(VERT_LIGHT)
    canvas.rect(x, y - height, width, height, fill=1, stroke=0)
    canvas.setFillColor(VERT_PRIMARY)
    canvas.rect(x, y - height, 3, height, fill=1, stroke=0)
    canvas.setFont("Helvetica-Bold", 8.5)
    canvas.setFillColor(VERT_PRIMARY)
    canvas.drawString(x + 8, y - height + 3.5, label.upper())
    return y - height - 6

def draw_paragraph(canvas, text, x, y, width, style=style_body):
    p = Paragraph(text, style)
    w_used, h_used = p.wrap(width, 1000)
    p.drawOn(canvas, x, y - h_used)
    return y - h_used - 4

def draw_bullet_item(canvas, text, x, y, width, style=style_body):
    canvas.setFillColor(VERT_ACCENT)
    canvas.rect(x + 2, y - 7.5, 3, 3, fill=1, stroke=0)
    p = Paragraph(text, style)
    w_used, h_used = p.wrap(width - 10, 1000)
    p.drawOn(canvas, x + 10, y - h_used)
    return y - h_used - 3.5

# ── HEADER ULTRA-PROFESSIONNEL ──────────────────────────
c.setFillColor(VERT_PRIMARY)
c.rect(0, H - 32*mm, W, 32*mm, fill=1, stroke=0)
c.setFillColor(VERT_ACCENT)
c.rect(0, H - 34*mm, W, 2*mm, fill=1, stroke=0)

if os.path.exists("logo.png"):
    c.drawImage("logo.png", 14*mm, H - 27*mm, width=20*mm, height=20*mm, mask='auto')

c.setFont("Helvetica-Bold", 24)
c.setFillColor(BLANC)
c.drawString(39*mm, H - 14*mm, "AGROSCAN AI")

c.setFont("Helvetica", 10.5)
c.setFillColor(VERT_MUTED)
c.drawString(39*mm, H - 20*mm, "Votre assistant mobile intelligent de diagnostic des plantes")

c.setFont("Helvetica-Oblique", 8.5)
c.setFillColor(BLANC)
c.drawString(39*mm, H - 27*mm, "Détectez plus tôt  ·  Intervenez plus vite  ·  Protégez mieux vos cultures")

# ── MISE EN PAGE : 2 COLONNES ───────────────────────────
MX   = 14*mm
GAP  = 6*mm
CW   = (W - 2*MX - GAP) / 2
LX   = MX
RX   = MX + CW + GAP
TOP  = H - 39*mm

c.setStrokeColor(VERT_MUTED)
c.setLineWidth(0.3)
c.setStrokeAlpha(0.3)
c.line(W/2, TOP, W/2, 16*mm)
c.setStrokeAlpha(1.0) 

# ══════════ COLONNE GAUCHE ══════════════════════════════
y = TOP

y = draw_section_header(c, "À propos", LX, y, CW)
y = draw_paragraph(c, "<b>AgroScan AI</b> rationalise le suivi agricole en convertissant une simple photo terrain en un diagnostic de santé des plantes instantané. Grâce à la vision par ordinateur, l'application identifie précisément la culture, qualifie la pathologie et propose des protocoles de traitement actionnables.", LX + 2, y, CW - 4)

y = draw_section_header(c, "Fonctionnalités Clés", LX, y, CW)
features = [
    "<b>Identification automatique</b> instantanée de la variété de culture.",
    "<b>Diagnostic global complet</b> de l'état sanitaire du feuillage.",
    "<b>Indice de confiance statistique</b> transparent pour chaque prédiction.",
    "<b>Description didactique</b> des symptômes biologiques observés.",
    "<b>Recommandations de traitement</b> raisonnées et fiches pratiques.",
    "<b>Plateforme</b> disponible sur terminaux Android."
]
for f in features:
    y = draw_bullet_item(c, f, LX, y, CW)

y -= 3
y = draw_section_header(c, "Exemples de Diagnostics", LX, y, CW)

table_data = [
    ["Culture", "Diagnostic Pathologique", "Confiance"],
    ["Maïs", "Plante parfaitement saine", "86 %"],
    ["Maïs", "Rouille commune (Puccinia)", "87 %"],
    ["Tomate", "Mildiou (Phytophthora)", "88 %"]
]
diag_table = Table(table_data, colWidths=[CW*0.24, CW*0.56, CW*0.20])
diag_table.setStyle(TableStyle([
    ('BACKGROUND', (0, 0), (-1, 0), VERT_PRIMARY),
    ('TEXTCOLOR', (0, 0), (-1, 0), BLANC),
    ('FONTNAME', (0, 0), (-1, 0), 'Helvetica-Bold'),
    ('FONTSIZE', (0, 0), (-1, 0), 7.5),
    ('BOTTOMPADDING', (0, 0), (-1, 0), 4),
    ('TOPPADDING', (0, 0), (-1, 0), 4),
    ('ALIGN', (0, 0), (1, -1), 'LEFT'),
    ('ALIGN', (2, 0), (2, -1), 'CENTER'),
    ('BACKGROUND', (0, 1), (-1, 1), VERT_LIGHT),
    ('BACKGROUND', (0, 2), (-1, 2), BLANC),
    ('BACKGROUND', (0, 3), (-1, 3), VERT_LIGHT),
    ('FONTNAME', (0, 1), (-1, -1), 'Helvetica'),
    ('FONTSIZE', (0, 1), (-1, -1), 7.5),
    ('TEXTCOLOR', (0, 1), (-1, -1), ANTHRACITE),
    ('BOTTOMPADDING', (0, 1), (-1, -1), 4),
    ('TOPPADDING', (0, 1), (-1, -1), 4),
    ('LINEBELOW', (0, 0), (-1, -1), 0.4, VERT_MUTED),
]))
w_t, h_t = diag_table.wrap(CW, 1000)
diag_table.drawOn(c, LX, y - h_t)
y = y - h_t - 10

y = draw_section_header(c, "Public Cible & Usages", LX, y, CW)
targets = [
    "<b>Exploitants agricoles</b> désireux de sécuriser leurs rendements.",
    "<b>Conseillers et techniciens</b> en charge des audits de cultures.",
    "<b>Coopératives agricoles</b> cherchant à mutualiser les outils de suivi.",
    "<b>Milieu academique</b> utilisant l'application pour la recherche en pathologie des plantes.", 
]
for t in targets:
    y = draw_bullet_item(c, t, LX, y, CW)

# ══════════ COLONNE DROITE ══════════════════════════════
y = TOP

y = draw_section_header(c, "Pourquoi AgroScan AI ?", RX, y, CW)
y = draw_paragraph(c, "Les pathologies des plantes non détectées se propagent de manière exponentielle, menaçant des parcelles entières. <b>AgroScan IA</b> démocratise l'accès à une expertise agronomique de premier niveau, permettant de réagir avant l'infestation globale tout en rationalisant l'usage des intrants.", RX + 2, y, CW - 4)

y = draw_section_header(c, "Analyses de Cas Concrets", RX, y, CW)
cases = [
    ("Maïs sain — Confiance 86 %", [
        "Tiges vigoureuses, feuillage d'un vert franc standard.",
        "<b>Préconisation :</b> Maintien du calendrier d'irrigation et suivi normal."
    ]),
    ("Rouille commune du maïs — Confiance 87 %", [
        "Pustules poussiéreuses brun-cannelle sur les surfaces foliaires.",
        "<b>Préconisation :</b> Recours à des variétés résistantes, aération des rangs."
    ]),
    ("Mildiou de la tomate — Confiance 88 %", [
        "Flétrissement rapide, larges taches brunes d'aspect huileux.",
        "<b>Préconisation :</b> Supprimer les résidus, abriter de l'humidité stagnante."
    ])
]
for title, points in cases:
    y = draw_paragraph(c, title, RX + 2, y, CW - 4, style=style_case_title)
    for p in points:
        y = draw_bullet_item(c, p, RX, y, CW)
    y -= 2

y -= 1
y = draw_section_header(c, "Valeur Ajoutée Unique", RX, y, CW)
values = [
    "<b>Gain de temps critique</b> via un diagnostic terrain en quelques secondes.",
    "<b>Économies substantielles</b> en ciblant précisément les zones à traiter.",
    "<b>Outil d'aide à la décision</b> clair, accessible sans formation lourde.",
]
for v in values:
    y = draw_bullet_item(c, v, RX, y, CW)

y -= 3
y = draw_section_header(c, "Fiche Projet & Contact", RX, y, CW)

contact_data = [
    ["Projet :", "AgroScan AI — Diagnostic des plantes par Vision par Ordinateur"],
    ["Porteur :", "Mbele Ngono Felix Alexis"],
    ["Spécification :", "Vision par Ordinateur & Mobile Kotlin"],
    ["Courriel :", "famngono@etu.uqac.ca"],
    ["Statut :", "Prototype Fonctionnel / MVP"]
]
contact_table = Table(contact_data, colWidths=[CW*0.20, CW*0.80])
contact_table.setStyle(TableStyle([
    ('FONTNAME', (0, 0), (0, -1), 'Helvetica-Bold'),
    ('FONTNAME', (1, 0), (1, -1), 'Helvetica'),
    ('FONTSIZE', (0, 0), (-1, -1), 7.5),
    ('TEXTCOLOR', (0, 0), (0, -1), VERT_PRIMARY),
    ('TEXTCOLOR', (1, 0), (1, -1), ANTHRACITE),
    ('BOTTOMPADDING', (0, 0), (-1, -1), 3.5),
    ('TOPPADDING', (0, 0), (-1, -1), 3.5),
    ('LINEBELOW', (0, 0), (-1, -1), 0.3, colors.HexColor("#EEEEEE")),
]))
w_c, h_c = contact_table.wrap(CW, 1000)
contact_table.drawOn(c, RX, y - h_c)

# ── FOOTER AVEC CLAUSE DE RESPONSABILITÉ ─────────────────
FH = 12*mm
c.setFillColor(colors.HexColor("#ECEFF1"))
c.rect(0, 0, W, FH, fill=1, stroke=0)
c.setStrokeColor(VERT_MUTED)
c.setLineWidth(0.5)
c.line(0, FH, W, FH)

c.setFont("Helvetica-Oblique", 6.8)
c.setFillColor(GRIS_TEXT)
c.drawCentredString(W / 2, FH / 2 - 1, "Avis légal : AgroScan IA est un outil d'accompagnement algorithmique et ne remplace pas une expertise agronomique réglementaire.")

c.save()
print(f"PDF généré : {OUTPUT_PDF}")