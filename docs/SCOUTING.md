# Scouting & lab samples

**Help → Scouting & lab samples…** opens one window with five tabs:

| Tab | What it does |
| --- | --- |
| Findings | Your scouting log. Each finding has a date, a bed or block, the crop and variety (picked from this or last season's plan, or typed), a category (insect/mite, fungal or water mold, bacterial, viral, abiotic, unknown), the suspected pest or disease, symptoms, severity, % of plants affected, photos (see below), and the action taken. A Photos column shows how many photos each finding has. |
| Lab samples | Samples sent to a lab or clinic. Each sample links to a finding, or to a soil block or water source. It records the type, lab, collected and shipped dates, tracking number, status (collected, shipped, received, results in), cost, a result summary or report file, and the date results came back. |
| Pest & disease guide | 13 common PNW cut-flower problems with what to look for and scouting tips. Each one links to the extension page it was taken from. |
| Labs | Washington labs and clinics, with what they accept, address, phone, form link and listed fees. See [LAB-SAMPLES.md](LAB-SAMPLES.md). |
| How to send a sample | Packing guides for diseased plants, tape lifts and swabs, insects, soil, and irrigation or well water, taken from the labs' own instructions. |

## Overdue results

A sample counts as **overdue** when there are no results N days after it was shipped (or collected, if it has not
shipped yet). N defaults to 14 and can be set from 1 to 120 with **Overdue after…**. None of the labs publish a
turnaround time, so this is a reminder for the farm, not a promise from the lab. Overdue samples sort to the top
and the window headline counts them.

## Today's reminders

**Help → Today's reminders…** includes scouting items, listed after the harvest, task and cooler items:

* **Lab results overdue · S-…** for each overdue sample (up to 3), with the lab, the shipped or collected date and
  how many days ago, and a reminder to call or email the lab with the sample ID.
* **Scouting · High/Severe … · bed** for each High or Severe finding from the last 7 days (up to 3), with the crop,
  % affected, and the linked lab sample's status, or "No lab sample yet" with a pointer to the How to send a sample
  tab.

Opening one of these items opens the Scouting & lab samples window. If the scouting log can't be read, the list shows
one "Scouting log needs attention" item instead of failing, and the log file is left untouched.

## Photos

Select a finding, then use **Attach photo…** to add a JPEG, PNG, WebP or HEIC/HEIF photo (up to 15 MB) with an optional
caption. **Photos…** lists a finding's photos. From there you can:

* **View** a photo, scaled down to fit. HEIC and WebP photos can't be previewed in the app; save a copy to view them.
* **Save a copy…** somewhere else.
* **Remove from finding**.

Photos go through the app's existing attachments feature: the same image checks, the 15 MB limit, and the same
`<records folder>/attachments/` folder, named by the photo's SHA-256. Every photo is checked against its SHA-256 and
size when opened, so a missing or changed file is reported instead of shown. The finding keeps the photo's SHA-256,
type, size, file name, caption and date in the scouting log. The original file stays where it was.

* Attaching the same picture twice keeps one entry. A finding holds up to 12 photos.
* Editing a finding never drops its photos.
* Removing a photo only unlinks it. The stored copy stays in the attachments folder and in backups, so you can
  attach it again.
* Backups copy the whole records folder, so they include the photos.

The attachments database table only accepts production tasks today. Linking photos to findings there would need a
migration, which waits until the open migration pull requests land (see below). The older free-text "Photo file"
field is still shown for findings that use it.

## Submission sheet

**Save submission sheet…** saves a one-page HTML file. Open it in a browser and print it. It is filled in with the
farm name, location, phone and email from the farm profile, plus the sample ID, lab and lab address, dates, crop,
variety, bed, suspected problem, symptoms, severity, % affected, action taken, and the packing checklist for that
sample type. It goes in the box **with** the lab's own signed form. It does not replace that form.

## Where the data lives

The log is saved to `<records folder>/scouting/scouting-log.json`:

* Every save writes a temporary file, flushes it to disk, then swaps it in (`SafeFiles`). A crash or power cut
  leaves either the old log or the new one, never half of one.
* The previous version is kept as `scouting-log.json.bak`.
* If the log cannot be read, FlowersForever opens the `.bak` copy, keeps the damaged file aside as
  `scouting-log.damaged-<date-time>.json`, and says so. If neither copy can be read, it refuses to save, so nothing
  is overwritten.
* Backups and restores (File → Back up now / Restore) copy the whole records folder, so they include the scouting
  log and the photos in `attachments/`.

**Why a file and not database tables:** a stack of open pull requests adds database migrations V22–V46. Adding new
tables now would collide with those migrations when they land. The log can move into the database later. The file
format has a `version` field, and unknown fields are ignored, so a newer file still opens.

## Pest & disease reference sources

All retrieved **October 7, 2026**. The app text is a short paraphrase. Read the source for full details and
management options. Scientific names are included only where the source page gives them.

| Entry | Source |
| --- | --- |
| Botrytis gray mold | PNW Plant Disease Management Handbook, Greenhouse ornamentals – Gray mold: https://pnwhandbooks.org/plantdisease/host-disease/greenhouse-plants-ornamental-gray-mold |
| Powdery mildew | PNW Handbook, Greenhouse ornamentals – Powdery mildew: https://pnwhandbooks.org/plantdisease/host-disease/greenhouse-plants-ornamental-powdery-mildew (also Dahlia – Powdery mildew: https://pnwhandbooks.org/plantdisease/host-disease/dahlia-powdery-mildew) |
| Downy mildew (snapdragon) | PNW Handbook, Snapdragon – Downy mildew: https://pnwhandbooks.org/plantdisease/host-disease/snapdragon-antirrhinum-majus-downy-mildew |
| Rust (snapdragon) | PNW Handbook, Snapdragon – Rust: https://pnwhandbooks.org/plantdisease/host-disease/snapdragon-antirrhinum-majus-rust |
| Aphids | WSU HortSense, Common insects and mites – Aphids: https://hortsense.cahnrs.wsu.edu/fact-sheet/common-insects-and-mites-aphids/ |
| Thrips | WSU HortSense, Rose – Thrips: https://hortsense.cahnrs.wsu.edu/fact-sheet/rose-thrips/ |
| Spider mites | WSU HortSense, Common insects and mites – Spider mites: https://hortsense.cahnrs.wsu.edu/fact-sheet/common-insects-mites-spider-mites/ |
| Western spotted cucumber beetle | WSU HortSense: https://hortsense.cahnrs.wsu.edu/fact-sheet/cucumber-pumpkin-squash-western-spotted-cucumber-beetle/ |
| Slugs | WSU HortSense: https://hortsense.cahnrs.wsu.edu/fact-sheet/cucumber-pumpkin-squash-slugs/ |
| Fusarium wilt | PNW Handbook, Carnation – Fusarium wilt: https://pnwhandbooks.org/plantdisease/host-disease/carnation-dianthus-caryophyllus-fusarium-wilt |
| Verticillium wilt | PNW Handbook, Chrysanthemum – Verticillium wilt: https://pnwhandbooks.org/plantdisease/host-disease/chrysanthemum-verticillium-wilt |
| Damping-off | PNW Handbook, Greenhouse ornamentals – Rhizoctonia damping-off: https://pnwhandbooks.org/plantdisease/host-disease/greenhouse-plants-ornamental-rhizoctonia-damping-root-stem-rot and Pythium: https://pnwhandbooks.org/node/2829/print |
| INSV / TSWV | PNW Handbook, Greenhouse ornamentals – Impatiens necrotic spot: https://pnwhandbooks.org/plantdisease/host-disease/greenhouse-plants-ornamental-impatiens-necrotic-spot (also Dahlia – Virus diseases: https://pnwhandbooks.org/plantdisease/host-disease/dahlia-virus-diseases) |

The guide is for scouting only. Confirm a diagnosis with a lab before spraying or pulling plants.
