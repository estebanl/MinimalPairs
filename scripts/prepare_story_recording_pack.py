#!/usr/bin/env python3
"""Build participant PDFs and blank collection templates from the Stories catalog.

Requires reportlab for PDF creation. No audio is generated, collected, or uploaded.
Use the Codex bundled Python runtime or an environment containing reportlab.
"""
import argparse
from datetime import datetime
import hashlib
import json
from pathlib import Path
import subprocess
from xml.sax.saxutils import escape
import zipfile
from zoneinfo import ZoneInfo

from evaluate_human_stories import ROOT, load_catalog


UNRELATED = {
    "sheep_vs_ship": "The green bicycle is beside the window.",
    "pen_vs_pan": "Seven lemons rolled across the table.",
    "heart_vs_hut": "The gardener parked a car by the gate.",
    "fool_vs_full": "The cook put the book on the shelf.",
    "pot_vs_port": "The clock stopped beside the warm stove.",
    "pen_vs_pin": "The red ticket is under the window.",
    "cat_vs_cut": "The black umbrella is next to the door.",
}

PARTICIPANT_STEPS = [
    "Use your usual accent and normal speaking voice. This is a test of the app, not of you. "
    "You do not need to copy a British accent or make words sound different if they are the same in your speech.",
    "Record in the room and with the microphone assigned by the coordinator. Keep its position steady. "
    "Use separate clips, not one long recording. Tell the coordinator if you change devices or settings.",
    "For each clip: start recording, leave about half a second of quiet, read one sentence once, "
    "leave a little quiet, then stop. Do not say your name, the task code, a letter, or a take number aloud.",
    "Follow the six numbered takes on each story card. Read the words exactly, even if a sentence seems unusual. "
    "Do not add an explanation or correct a word while the clip is still recording.",
    "Speak naturally for the extra reading too. Do not deliberately mumble, stretch sounds, add noise, "
    "or try to force an unclear vowel. Tell the coordinator off-record whether you chose A or B.",
    "Keep every attempted take, including slips, pauses and unclear speech. A new take gets a new filename; "
    "it never replaces the old one. You may pause, take a break, or stop the session.",
    "Do not listen to app reference voices or look at app scores during collection. "
    "The coordinator must explain recording use and any service upload before collecting your permission.",
]


def build_plan(stories, order="ABBA"):
    if order not in ("ABBA", "BAAB"):
        raise ValueError("Use ABBA or BAAB for the four main readings")
    if set(stories) != set(UNRELATED):
        raise ValueError("Update unrelated prompts when the story catalog changes")
    targets, tasks = [], []
    for number, (story_id, story) in enumerate(stories.items(), 1):
        code = f"S{number:02d}"
        targets.append({
            "story_code": code, "story_id": story_id,
            "phrase_a": story["targetPhrase"], "phrase_b": story["alternate_phrase"],
            "word_a": story["intendedWord"], "word_b": story["trapWord"],
            "word_a_ipa": story["intendedIpa"], "word_b_ipa": story["trapIpa"],
            "phrase_a_ipa": story["targetPhraseIpa"],
            "phrase_b_ipa": " ".join(story["alternate_tokens"]),
            "target_phoneme_index": story["index"],
            "intended_phoneme": story["tokens"][story["index"]],
            "alternative_phoneme": story["alternate_tokens"][story["index"]],
            "unrelated_sentence": UNRELATED[story_id],
        })
        for take, letter in enumerate(order + "CU", 1):
            condition = {"A": "intended_prompt", "B": "alternate_prompt",
                         "C": "review_candidate", "U": "unrelated_prompt"}[letter]
            prompt = (story["targetPhrase"] if letter == "A" else story["alternate_phrase"] if letter == "B"
                      else UNRELATED[story_id] if letter == "U" else
                      "Choose A or B for one extra natural reading; record your choice off-record in the collection log.")
            tasks.append({"task_id": f"{code}-T{take:02d}", "story_code": code, "story_id": story_id,
                          "take": take, "prompt_condition": condition, "prompt_text": prompt,
                          "candidate_choice_a_or_b": "", "speaker_id": "", "session_id": "", "microphone_id": "",
                          "recording_id": "", "original_path": "", "evaluation_wav_path": "",
                          "recorded_at_with_timezone": "", "status": "not_recorded", "notes": ""})
    return {"schema_version": 1, "status": "collection_plan_only", "main_reading_order": order,
            "clips_per_speaker_microphone_session": len(tasks),
            "note": "Prompt conditions are collection instructions, never ground-truth expected labels. "
                    "Reviewers must determine expected after independent blinded listening. No recordings are supplied.",
            "targets": targets, "tasks": tasks}


def blank_metadata():
    return {
        "schema_version": 1, "status": "blank_template_no_participants",
        "speakers": [{"speaker_id": "", "accent_self_description": "", "accent_group_for_analysis": "",
                      "first_languages": [], "english_learning_context": "", "self_reported_mergers": "",
                      "speaker_split": "unassigned", "recruitment_stratum": "",
                      "evaluation_consent_confirmed": False, "service_upload_consent_confirmed": False,
                      "approved_upload_service": "", "consent_record_reference": ""}],
        "microphones": [{"microphone_id": "", "device_make_model": "", "microphone_make_model": "",
                         "microphone_type": "", "os_and_version": "", "recording_app_and_version": "",
                         "capture_format": "", "sample_rate_hz": None, "channels": None, "bit_depth": None,
                         "automatic_gain_control": "unknown", "noise_suppression": "unknown",
                         "echo_cancellation": "unknown"}],
        "sessions": [{"session_id": "", "speaker_id": "", "microphone_id": "",
                      "recorded_at_with_timezone": "", "room_description": "", "background_noise": "",
                      "microphone_distance_cm": None, "input_level_setting": "", "breaks_or_interruptions": "",
                      "original_storage_location": "", "main_reading_order": "", "story_order": [], "notes": ""}],
    }


def blank_annotations():
    return {"schema_version": 1, "status": "blank_template_no_annotations",
            "reviewer_form": {"clip_id": "", "story_id": "", "reviewer_id": "", "heard_word_or_description": "",
                              "label": "", "recording_quality_notes": "", "reviewer_confidence": "", "notes": ""},
            "allowed_labels": ["intended", "trap", "ambiguous", "unrelated"],
            "private_coordinator_map": {"clip_id": "", "recording_id": "", "speaker_id": "",
                                        "session_id": "", "microphone_id": "", "wav_path": ""},
            "adjudication_form": {"clip_id": "", "reviewer_a_id": "", "reviewer_a_label": "",
                                  "reviewer_b_id": "", "reviewer_b_label": "", "final_expected": "",
                                  "disagreement_resolution": "", "adjudicator_id": "", "annotation_reference": ""},
            "blinding": "Copy the reviewer_form separately for each reviewer. Supply opaque randomized clip IDs, "
                        "story IDs and clips only. Withhold the coordinator map, prompt conditions, speaker/accent/device "
                        "metadata, the other reviewer's answers, and all scorer outputs until annotations are locked."}


def write_json(path, data):
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n")


def register_fonts(font_path=None):
    from reportlab.pdfbase import pdfmetrics
    from reportlab.pdfbase.ttfonts import TTFont
    candidates = [Path(font_path)] if font_path else [
        Path("/System/Library/Fonts/Supplemental/Arial Unicode.ttf"),
        Path("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"),
        Path("C:/Windows/Fonts/arial.ttf")]
    font = next((path for path in candidates if path.is_file()), None)
    if font is None:
        raise ValueError("Supply --font with a TrueType font supporting the catalog IPA characters")
    pdfmetrics.registerFont(TTFont("PackSans", str(font)))
    required = "ɑɔəɒæɪʌʊθðŋʃː"
    if any(ord(char) not in pdfmetrics.getFont("PackSans").face.charToGlyph for char in required):
        raise ValueError("The selected font lacks required IPA glyphs")


class PackPDF:
    def __init__(self, path, title):
        from reportlab.pdfgen.canvas import Canvas
        from reportlab.lib import colors
        self.colors = colors
        self.canvas = Canvas(str(path), pagesize=(612, 792))
        self.canvas.setTitle(title)
        self.canvas.setAuthor("MinimalPairs")
        self.title = title
        self.number = 0
        self.y = 0
        self.fields = []

    def page(self, section, title, subtitle=""):
        c = self.canvas
        if self.number:
            c.showPage()
        self.number += 1
        c.setFillColor(self.colors.HexColor("#123843"))
        c.rect(0, 746, 612, 46, fill=1, stroke=0)
        c.setFillColor(self.colors.white)
        c.setFont("Helvetica-Bold", 11)
        c.drawString(44, 764, "MINIMALPAIRS  /  HUMAN RECORDING PACK")
        c.setFillColor(self.colors.HexColor("#405562"))
        c.setFont("Helvetica", 8)
        c.drawString(44, 25, f"Issue #9  |  {section}  |  No human recordings supplied")
        c.drawRightString(568, 25, str(self.number))
        self.y = 716
        self.text(title, 23, 29, after=8)
        if subtitle:
            self.text(subtitle, 10, 15, after=16)

    def text(self, value, size=10.5, leading=15, after=9, color="#203741"):
        from reportlab.platypus import Paragraph
        from reportlab.lib.styles import ParagraphStyle
        paragraph = Paragraph(escape(value).replace("\n", "<br/>"), ParagraphStyle(
            "pack", fontName="PackSans", fontSize=size, leading=leading, textColor=self.colors.HexColor(color)))
        _, height = paragraph.wrap(524, 700)
        if self.y - height < 48:
            raise ValueError(f"Page {self.number} overflows: {value[:80]}")
        paragraph.drawOn(self.canvas, 44, self.y - height)
        self.y -= height + after

    def heading(self, value):
        self.text(value, 13, 18, after=8, color="#146B6A")

    def field(self, name, label, height=25):
        c = self.canvas
        self.text(label, 9, 12, after=4)
        if self.y - height < 48:
            raise ValueError(f"Field {name} overflows")
        c.acroForm.textfield(name=name, tooltip=label, x=44, y=self.y - height, width=524, height=height,
                            borderWidth=.6, borderColor=self.colors.HexColor("#9BAEB2"),
                            fillColor=self.colors.HexColor("#F5F8F8"), textColor=self.colors.HexColor("#203741"),
                            fontName="Helvetica", fontSize=10, value="", forceBorder=True,
                            fieldFlags="multiline" if height > 30 else "")
        self.fields.append(name)
        self.y -= height + 9

    def phrase(self, letter, phrase):
        from reportlab.platypus import Paragraph
        from reportlab.lib.styles import ParagraphStyle
        c = self.canvas
        p = Paragraph(escape(phrase), ParagraphStyle("phrase", fontName="PackSans", fontSize=15, leading=21,
                                                   textColor=self.colors.HexColor("#123843")))
        _, height = p.wrap(464, 200)
        box_height = max(61, height + 28)
        c.setFillColor(self.colors.HexColor("#EAF3F2"))
        c.roundRect(44, self.y - box_height, 524, box_height, 8, fill=1, stroke=0)
        c.setFillColor(self.colors.HexColor("#146B6A"))
        c.setFont("Helvetica-Bold", 12)
        c.drawString(57, self.y - 26, letter)
        p.drawOn(c, 88, self.y - 15 - height)
        self.y -= box_height + 12

    def save(self):
        self.canvas.save()


def participant_pdf(path, plan):
    pdf = PackPDF(path, "Stories participant recording cards and metadata")
    pdf.page("Participant", "Record the Stories phrases", "A reusable session pack: 7 stories, 6 separate clips each, 42 clips per speaker and microphone.")
    for number, instruction in enumerate(PARTICIPANT_STEPS, 1):
        pdf.text(f"{number}. {instruction}", after=12)
    pdf.heading("Before you start")
    pdf.text("The coordinator supplies a speaker ID, session ID and microphone ID. Use those IDs on files; "
             "do not include a name, email address or phone number. Keep original recordings until transfer is confirmed.")
    pdf.text("These are collection prompts, not a pronunciation lesson. No score or correct/incorrect label is assigned during recording.", color="#146B6A")

    pdf.page("Metadata", "Speaker and session", "Use a pseudonymous speaker ID. Leave unknown details blank and tell the coordinator.")
    for name, label in [
        ("speaker_id", "Speaker ID (assigned by coordinator; no personal name)"),
        ("accent", "How do you describe your accent?"),
        ("languages", "First language(s) / languages used while growing up"),
        ("english_context", "Where and how did you learn or regularly use English? (optional)"),
        ("mergers", "Do any of today's word pairs sound the same in your usual accent? Which?"),
        ("session_id", "Session ID and microphone ID (assigned by coordinator)"),
        ("session_date", "Recording date, local time and time zone"),
        ("speaker_notes", "Other notes you want the coordinator to know (optional; no identifying details)"),
    ]:
        pdf.field(name, label, height=40 if name in ("mergers", "speaker_notes") else 25)
    pdf.text("Consent records and contact details are kept separately by the coordinator. Do not put them on this worksheet.", 9, 13)

    pdf.page("Metadata", "Microphone and room", "Complete one sheet per device/setup. The coordinator can help with technical settings.")
    for name, label in [
        ("microphone_id", "Microphone ID / session ID / speaker ID"),
        ("device", "Device make/model and microphone make/model (built-in, headset, USB, etc.)"),
        ("recorder", "Operating system and recording app, including versions if known"),
        ("format", "Original format, sample rate, channels and bit depth (or unknown)"),
        ("processing", "Automatic gain / noise suppression / echo cancellation: on, off or unknown for each"),
        ("room", "Room and background noise (ordinary quiet / fan / conversation, etc.)"),
        ("distance", "Approximate microphone distance and input level setting (or unknown)"),
        ("changes", "Changes, breaks or interruptions; identify affected take IDs"),
    ]:
        pdf.field(name, label, height=40 if name in ("processing", "changes") else 25)
    pdf.text("The evaluation copies must be 16 kHz, 16-bit mono PCM WAV. Keep originals unchanged; "
             "the coordinator can convert copies later. Do not apply pitch changes, voice conversion or speech synthesis.", 9, 13)

    for target in plan["targets"]:
        code, story_id = target["story_code"], target["story_id"]
        pdf.page("Participant", f"{code}  {target['word_a']} / {target['word_b']}",
                 f"Story ID: {story_id}. Say the whole sentence exactly once per clip. Do not say A, B or the task ID aloud.")
        pdf.phrase("A", target["phrase_a"])
        pdf.phrase("B", target["phrase_b"])
        pdf.heading("Six separate recordings")
        tasks = [row for row in plan["tasks"] if row["story_id"] == story_id]
        for task in tasks:
            condition = task["prompt_condition"]
            instruction = ("Read sentence A once." if condition == "intended_prompt" else
                           "Read sentence B once." if condition == "alternate_prompt" else
                           "Choose A or B and read it naturally once more. Tell the coordinator your choice off-record." if condition == "review_candidate" else
                           "Read this unrelated sentence once:")
            pdf.text(f"{task['task_id']}  {instruction}", 10.5, 15, after=7)
            if condition == "unrelated_prompt":
                pdf.text(target["unrelated_sentence"], 13, 18, after=10, color="#146B6A")
        pdf.text("The extra reading is only a candidate for review. It is not automatically an ambiguous pronunciation. "
                 "Use your normal voice; do not try to create an error.", 9, 13, after=10)
        if story_id == "fool_vs_full":
            pdf.text("Sentence B is intentionally unusual English. Read the printed words as they are; do not add or replace words.", 9, 13, color="#146B6A")
        pdf.text("Filename pattern: <speaker>_<session>_<microphone>_" + code + "_T01.wav. "
                 "Replace the IDs and take number. Additional attempts use T07, T08, etc.; never overwrite a take.", 9, 13)
    pdf.save()
    return {"pages": pdf.number, "fields": pdf.fields}


def coordinator_pdf(path, plan, revision):
    pdf = PackPDF(path, "Stories coordinator collection and blinded review guide")
    pdf.page("Coordinator", "Collect an independent dataset", "Issue #9 remains open until collection, blinded review, evaluation and reporting are complete.")
    for title, text in [
        ("1. Freeze the study before scoring", "Complete the study plan on page 2. Predeclare recruitment groups, speaker count, "
         "takes and device coverage, speaker-level development/held-out split, and acceptable false-ending/retry rates. "
         "This pack does not choose a statistically sufficient sample size or release threshold."),
        ("2. Obtain and document permission", "Explain evaluation, storage, retention/withdrawal arrangements and the named scoring service. "
         "Confirm recording-use permission and any upload permission separately. Keep names, contact details and signed records outside "
         "the repository; the metadata gets only a pseudonymous consent reference. A form in this pack is not proof of consent."),
        ("3. Run a natural recording session", "Use all seven cards on each assigned microphone. There are 42 planned clips per speaker/device "
         "session: two A, two B, one extra natural reading and one unrelated sentence per story. Assign ABBA or BAAB and rotate story order "
         "across speakers before collection; log the actual order. Do not present app scores or synthetic reference voices."),
        ("4. Retain evidence and real coverage", "Keep all attempts, including difficult takes. Preserve originals, technical settings and room notes. "
         "Do not label a take from its prompt, erase failures, duplicate clips to inflate counts, or manufacture ambiguous speech. "
         "Document dialect mergers. If reviewers find no ambiguous cases, report that gap and collect further approved natural speech "
         "under a predeclared supplemental-collection rule; do not relabel clear takes."),
        ("5. Review first, score second", "Use two independent blinded reviewers and lock labels before scorer access. Keep their IDs, disagreements "
         "and resolution. Evaluate all retained eligible takes using the existing tool; publish group counts, false endings, retries, "
         "uncertainty, infrastructure failures and limitations. A successful script exit does not establish calibration."),
    ]:
        pdf.heading(title)
        pdf.text(text, 10, 14, after=12)

    pdf.page("Study plan", "Freeze before the first score", f"Pack catalog revision: {revision[:12]}. Record the actual service/model used for evaluation.")
    for name, label in [
        ("dataset_id", "Dataset ID / protocol version / owner ID (no personal contact details)"),
        ("sample_plan", "Planned speakers, accents/language groups, microphones and takes per group"),
        ("split", "Speaker-level development/held-out assignment method and planned counts"),
        ("ordering", "ABBA/BAAB assignment and story-order rotation rule"),
        ("baseline", "App/catalog revision, backend version and checkpoint/model ID"),
        ("criteria", "Predeclared acceptable false-ending and retry rates, denominators and uncertainty method"),
        ("supplement", "Supplemental collection / stopping rule for missing groups or ambiguous cases"),
        ("service", "Approved upload service / consent reference location / retention and withdrawal arrangements"),
        ("freeze_date", "Freeze date with time zone / amendments and reasons"),
    ]:
        pdf.field(name, label, height=35 if name in ("sample_plan", "criteria", "service") else 25)

    pdf.page("Blinded review", "Label what is heard", "Give reviewers this page and a blank review form, plus randomized clips and story IDs. Withhold collection logs and scorer output.")
    pdf.text("Create opaque clip IDs and shuffle presentation separately for each reviewer. Remove speaker IDs, accents, microphones, "
             "prompt A/B choices and take order from filenames and file metadata supplied to reviewers. Keep the reverse mapping private. "
             "Reviewers need the word pair and fixed sentence context, not the word the speaker was asked to say.", after=14)
    pdf.heading("Label definitions")
    for text in [
        "intended: the recognizable story phrase contains word A's vowel/meaning.",
        "trap: the recognizable story phrase contains word B's vowel/meaning.",
        "ambiguous: no confident A/B decision can be made, including unresolved reviewer disagreement. Explain uncertainty and poor audio separately in the quality notes.",
        "unrelated: the utterance does not match the prompted story sentence. A similar vowel in a different sentence is still unrelated.",
    ]:
        pdf.text(text, 10, 14, after=8)
    pdf.heading("Reference pairs (not the prompted choice)")
    for target in plan["targets"]:
        pdf.text(f"{target['story_code']}  {target['story_id']}  |  A: {target['word_a']} /{target['word_a_ipa']}/  |  "
                 f"B: {target['word_b']} /{target['word_b_ipa']}/", 10, 14, after=6)
    pdf.text("Use phrase-targets.json for the fixed full sentence and IPA when needed. It lists both possible readings, never the "
             "choice for a particular clip. Reviewers must not consult collection-plan.json or the coordinator's map.", 9, 13, after=12)
    pdf.heading("Resolve and lock")
    pdf.text("Compare completed independent reviews. Record disagreements and resolution with reviewer/adjudicator IDs; retain unresolved "
             "cases as ambiguous. Never use the model to decide the human label. Preserve poor-audio cases and any exclusions with reasons. "
             "Lock final labels and speaker split before scoring. Do not tune on held-out speakers.", 10, 14)

    pdf.page("Blinded form", "One independent review per clip", "Duplicate this page or the JSON reviewer_form for every clip. Do not show one reviewer's answers to the other.")
    for name, label in [
        ("review_clip", "Opaque clip ID / story ID (provided in blinded review kit)"),
        ("reviewer_id", "Reviewer ID / review date"),
        ("heard", "Word or speech heard (describe without consulting the collection prompt)"),
        ("label", "Independent label: intended / trap / ambiguous / unrelated"),
        ("confidence", "Reviewer confidence and reason for uncertainty, if any"),
        ("quality", "Recording quality: noise, clipping, silence, overlap, incomplete phrase, etc."),
        ("review_notes", "Other notes / possible dialect merger (do not infer accent from a label)"),
    ]:
        pdf.field(name, label, height=48 if name in ("heard", "quality", "review_notes") else 25)
    pdf.text("No speaker, accent, microphone, prompt-condition or scorer-score fields belong on this form.", 9, 13, color="#146B6A")

    pdf.page("Handoff", "From recordings to evaluation", "Use the existing evaluator only after consent, technical checks and final blinded labels are complete.")
    pdf.heading("Private folder layout")
    pdf.text("Keep a private dataset folder outside this repository. Separate originals/, wav/, metadata/, annotations/ and consent/. "
             "The consent directory and identity mapping require separate access. Give reviewers only a sanitized blinded kit. "
             "Record original and converted file hashes, conversion commands, exclusions and all additional attempts.", 10, 14)
    pdf.heading("Technical checks")
    pdf.text("Create intact evaluation copies as 16 kHz, 16-bit, mono PCM WAV; each clip must be 80 ms to 30 seconds and no larger than 1 MB. "
             "Do not splice out difficult speech or alter vowels. No denoising, pitch change, synthetic speech or voice conversion. "
             "Log unavoidable device processing as on/off/unknown. Keep original files unchanged.", 10, 14)
    pdf.heading("Build the evaluator manifest")
    pdf.text("Start with manifest.empty.json. Add actual clips only after independent review. Join final labels to speaker, microphone and "
             "session metadata using the private clip map. Each row needs recording_id, speaker_id, accent, microphone, source_type=human, "
             "consent_confirmed=true, story_id, expected, annotation and wav_path. Set expected from final_expected, never prompt_condition. "
             "Set consent_confirmed true only after both collection-use and applicable service-upload permissions are confirmed.", 10, 14)
    pdf.text("Use the same accent-group spelling and microphone ID/description for every relevant row. annotation should reference both "
             "reviewers, their decisions and the resolution record. wav_path is relative to the manifest. Top-level provenance must describe "
             "recruitment, collection dates, consent references, conversion and blinded review without participant identities. "
             "Create separate development and held-out manifests; every session for a speaker stays in the same split.", 10, 14)
    pdf.heading("Run and report")
    pdf.text("From the repository, run scripts/evaluate_human_stories.py with the private manifest, approved endpoint and output report path. "
             "Remote services require HTTPS; the key comes from an environment variable. Each clip is uploaded twice, once per contrast "
             "direction. The empty template is deliberately rejected and cannot be reported as a completed study.", 10, 14)
    pdf.text("Check all seven stories, four final labels and planned speaker/device groups. Report false-ending and retry rates with their "
             "denominators, agreement, uncertainty, missing coverage and limitations. Treat failures separately from speech retries. "
             "Document threshold changes and regression cases, then use untouched held-out speakers. Keep confidence labelled experimental.", 10, 14)
    pdf.save()
    return {"pages": pdf.number, "fields": pdf.fields}


def create_pack(output, order="ABBA", font=None):
    output = Path(output)
    # Never overwrite a collected or edited pack, especially filled metadata forms.
    if output.exists():
        raise ValueError("Output directory already exists; choose a new empty destination")
    stories = load_catalog()
    plan = build_plan(stories, order)
    register_fonts(font)
    revision = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    output.mkdir(parents=True)
    pdf_dir = output / "output/pdf"
    pdf_dir.mkdir(parents=True)
    templates = output / "templates"
    templates.mkdir()
    write_json(output / "collection-plan.json", plan)
    write_json(output / "phrase-targets.json", {"schema_version": 1, "catalog_commit": revision,
                                               "target_index_convention": "zero-based space-separated IPA token index",
                                               "stories": plan["targets"]})
    write_json(templates / "speaker-microphone-session.json", blank_metadata())
    write_json(templates / "blinded-review-and-adjudication.json", blank_annotations())
    write_json(templates / "manifest.empty.json", {"schema_version": 1, "dataset_id": "replace-with-study-and-version",
        "provenance": "Replace after collection with recruitment, collection, consent, conversion and blinded annotation provenance.",
        "recordings": []})
    pdfs = {
        "output/pdf/participant-recording-cards.pdf": participant_pdf(pdf_dir / "participant-recording-cards.pdf", plan),
        "output/pdf/coordinator-and-review-guide.pdf": coordinator_pdf(pdf_dir / "coordinator-and-review-guide.pdf", plan, revision),
    }
    (output / "START_HERE.txt").write_text(
        "STORIES HUMAN RECORDING PACK - ISSUE #9\n\n"
        "Status: collection preparation only. No human recordings or annotations supplied.\n\n"
        "1. Coordinator: read output/pdf/coordinator-and-review-guide.pdf and freeze the study plan.\n"
        "2. Confirm recording-use and applicable service-upload permissions; keep consent/identities private.\n"
        "3. Give the participant output/pdf/participant-recording-cards.pdf. Its metadata pages are fillable.\n"
        "4. Record 42 separate clips per speaker/microphone session. Duplicate the log for additional sessions.\n"
        f"   This pack's four main readings use {order}; assign the reverse order across other speakers as planned.\n"
        "5. Fill templates/speaker-microphone-session.json and the actual take details in collection-plan.json.\n"
        "   Prompt condition is not ground truth. The extra natural take is NOT automatically ambiguous.\n"
        "6. Create randomized clip IDs and a private reverse map. Two reviewers work independently.\n"
        "   Give reviewers only sanitized clips, story IDs, phrase-targets.json and their blank forms.\n"
        "   Do not give them collection-plan.json, speaker/device metadata, other reviews or model outputs.\n"
        "7. Resolve and lock annotations, document dialect mergers and check four-label coverage.\n"
        "8. Convert evaluation copies to 16 kHz/16-bit/mono PCM WAV; retain original files and hashes.\n"
        "9. Fill manifest.empty.json with real reviewed, consented clips. Keep all sessions from a speaker\n"
        "   in one split, create separate development/held-out manifests, then run the existing evaluator.\n\n"
        "Evaluator command (from the repository; replace placeholders):\n"
        "  python3 scripts/evaluate_human_stories.py /private/study/manifest.json \\\n"
        "    --endpoint https://APPROVED-SERVICE --output /private/study/report.json\n"
        "Use PHONEME_SCORER_FUNCTION_KEY for the optional service key; never put a key in files or URLs.\n"
        "Do not run this against human audio until upload permission and the endpoint are confirmed.\n\n"
        "No default speaker count or release threshold is claimed. Predeclare both before scoring.\n"
        "One extra natural take does not guarantee ambiguous coverage; report gaps honestly.\n"
        "Keep actual audio, filled forms, identity maps and consent records outside the repository.\n"
        "The empty evaluator manifest deliberately fails validation; it is not a study result.\n\n"
        f"Catalog revision: {revision}\n"
        "Protocol: docs/STORIES_HUMAN_VALIDATION.md\n"
        "Issue: https://github.com/estebanl/MinimalPairs/issues/9\n")
    index = {"schema_version": 1, "created_on": datetime.now(ZoneInfo("America/Toronto")).date().isoformat(),
             "catalog_commit": revision, "catalog_sha256": hashlib.sha256((ROOT /
                "composeApp/src/commonMain/kotlin/com/minimalpairs/app/data/catalog/MeaningTrapCatalog.kt").read_bytes()).hexdigest(),
             "study_status": "not_collected", "stories": len(stories), "planned_clips_per_session": len(plan["tasks"]),
             "pdfs": pdfs, "files_sha256": {str(p.relative_to(output)): hashlib.sha256(p.read_bytes()).hexdigest()
                                           for p in sorted(output.rglob("*")) if p.is_file()}}
    write_json(output / "pack-index.json", index)
    archive = output.with_suffix(".zip")
    if archive.exists():
        raise ValueError("ZIP destination already exists; refusing to overwrite it")
    with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as zipped:
        for path in sorted(output.rglob("*")):
            if path.is_file():
                zipped.write(path, Path("stories-recording-pack") / path.relative_to(output))
    return archive, index


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True, help="New output folder; existing folders are never overwritten")
    parser.add_argument("--order", choices=("ABBA", "BAAB"), default="ABBA")
    parser.add_argument("--font", type=Path, help="TrueType font supporting IPA (auto-detected on common systems)")
    args = parser.parse_args()
    archive, index = create_pack(args.output, args.order, args.font)
    print(json.dumps({"archive": str(archive.resolve()), "stories": index["stories"],
                      "planned_clips_per_session": index["planned_clips_per_session"], "pdfs": index["pdfs"]}, indent=2))


if __name__ == "__main__":
    main()
