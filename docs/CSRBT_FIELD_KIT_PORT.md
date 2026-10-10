# CSRBT field kit in FlowersForever

FlowersForever's **Field science** tab opens selected instruments from the
[CSRBT field-science kit](https://github.com/RicheyWorks/CSRBT/blob/5990b9c21e419f5836c9f0a22f15d993403557a3/README.md).
The bundled source is pinned to CSRBT commit
`5990b9c21e419f5836c9f0a22f15d993403557a3`; its HTML pages, source
attribution, and MIT license are packaged together. This is a contextual way to
reach CSRBT's existing browser tools, not a new crop model or a replacement for
the farm's saved plans.

The bundle includes all 41 upstream HTML pages plus a searchable FlowersForever library index. Select **Browse all farm tools** in Field science to reach every page directly, including specialist tools and supporting guides.
[PROVENANCE.txt](../src/main/resources/csrbt-field-kit/PROVENANCE.txt) records
the commit, source selection, checksums, and the one repaired relative link in
an internal reference page; the curated instrument pages are otherwise copied
from that commit.

The bundled [license](https://github.com/RicheyWorks/CSRBT/blob/5990b9c21e419f5836c9f0a22f15d993403557a3/LICENSE)
covers CSRBT's code and documentation. Soil Recipes also names and links the
growers whose published recipes it describes; the license is not a claim that
CSRBT originated those recipes.

## How to use it

1. Choose a farm type, or combine types, and open **Field science** in the desktop app.
2. Choose a card and select **Open offline tool**. The app extracts the pinned kit
   into its local data directory and opens that page in the default browser.
3. Enter observations in the browser tool. Use its **Copy**, **Export**, or **Print**
   action to keep results you need.
4. To keep a text export with this farm, select **Save an export…** on that
   instrument's card. Paste copied text, browse for one `.eco`, `.txt`, or `.csv`
   file, or drop text or one such file into the draft. Files must be UTF-8 text;
   the reviewed text may be at most **512 KiB** in UTF-8. Give it a title, add
   an optional note, review or edit the text, then choose **Save export**.
   Dropping or browsing only stages a draft; canceling saves nothing. Select a
   row under **Saved field science exports** to read or copy the saved text.
   Choose **Save selected text…** to write that archived reviewed text to a
   UTF-8 `.eco`, `.txt`, or `.csv` file. This action is also available to
   VIEWER after the selected body loads. It saves only the text body, with its
   saved BOM, line endings, and trailing spaces. It does not recreate the
   original browser file or add provenance metadata. If the destination exists,
   confirm **Replace existing export?** before it is replaced.
   Search by title, instrument, filename, or note across the full archive;
   select **Search exports** or press Enter, and use **Clear search** to return
   to all entries. Search does not inspect the saved text itself. **Previous**
   and **Next** browse matching entries, 200 at a time. The archive remains
   available when the current farm type has no matching offline tool cards.
5. When a tool informs something you actually observed or tried on your farm,
   select **Record a finding** on that tool's card. Review the editable Knowledge
   Log draft, describe the real observation and any follow-up, then confirm
   the dialog to save. The draft identifies the selected CSRBT tool with its
   title, source URL, and a `field-science` tag. Canceling leaves no farm
   record. Record any operational decision separately in the relevant
   FlowersForever plan.
6. To link a finding to text already saved in the archive, select an export
   and wait for its text to load, then choose **Record finding from selected…**.
   Write and review your own observation in the editable Knowledge Log draft;
   the reviewed export text and any calculations are not copied into it as an
   observation. The saved finding links to that exact H2 export by its saved
   ID. In the saved Knowledge Log detail, choose **Open linked export** to read
   the archived text. This link does not point to the original browser file.

Some browser pages retain local drafts; recorders offer their own export or
print actions. Opening a page does **not** write to FlowersForever's H2
database, automatically update batches, stock, orders, or customer records,
or apply desktop OWNER/HAND/VIEWER roles inside the browser. An OWNER or HAND
can save a reviewed export or finding in the desktop app; a VIEWER can search,
read, save a selected archive text to a file, and open an export linked from a
saved finding, but cannot create farm records.

**Save an export** archives the exact reviewed text in H2 with the selected
card's instrument identity, source URL and pinned CSRBT commit, plus its title,
optional filename and note, byte length, and SHA-256 hash. The hash covers the
saved UTF-8 text, including any edits made during review. It does not hash the
original file or prove that the text came from the selected tool. Choose the
correct card and check the content before saving. The app stores a text copy;
it does not parse `.eco` or `.csv` measurements or attach the original file.

The reviewed text enters the [FlowersForever data-directory backup](BACKUP_RESTORE.md)
with H2. Browser-local drafts, separately downloaded originals, and files
exported from the browser do not enter H2 through this action; keep those
files separately when needed. Browser storage can be cleared, and opening a
future kit version may use a different local page address. Treat a browser
draft as a convenience, not a backup.

**Save selected text…** makes a separate file from an already saved H2 entry.
It writes the archived body as UTF-8 without adding a title, source URL, or
extra newline. Before writing, the app checks the body against its saved byte
length and SHA-256. Canceling or a failed write leaves an existing destination
in place. A file written this way is a copy of the reviewed text, not a
recovery of the original browser download. The original file and browser
drafts remain separate, and the new file needs its own backup if kept outside
the FlowersForever data directory.

**Record a finding** on a tool card carries its source attribution into an
editable Knowledge Log draft without attaching an archive entry. **Record
finding from selected…** carries the selected reviewed H2 export's ID and
source attribution into the draft. Neither action reads the browser page or
turns tool output into your observation. The archive keeps reviewed source
text, while the Knowledge Log keeps the finding you choose to write and, when
selected, a link to that exact archive entry. Both saved records are included
in the H2 data-directory backup; browser drafts and original files remain
separate. A recipe, model, or reference page alone is not evidence that a
practice worked on your farm.

The full bundled site supports its own internal links. The **Field science**
cards below are the curated starting points; an internal link can lead to a
page outside a farm's card list. In a combined farm, relevant cards from its
selected types appear together without duplicate cards. Changing the farm mix
changes which cards are offered; it does not delete CSRBT browser drafts.

## Placement by farm type

| Farm type | Field science cards | Why these fit |
|---|---|---|
| Rose & events | Soil Bench; Farm Scout | Soil and compost work, pest and pollinator observations for growing flowers. Wedding and bouquet work stays in the farm app. |
| Wildflower farm | Soil Bench; Farm Scout; Relevé | Growing observations plus an optional botanical vegetation plot. |
| Flower shop | None by default | Retail and design work do not imply field-science data collection. |
| Cannabis farm | Soil Recipes (Subcool’s Super Soil); Soil Bench; Farm Scout; Greenhouse Monitor; Breeding Bench; Pheno Tracker | Grower recipe reference, soil, scouting, protected growing, and optional selection work. The recipe is a published grower convention, not a prescribed input. Breeding Bench's crop presets do not include cannabis. |
| Potato farm | Soil Bench; Farm Scout | Soil, pest counts, germination, and annual crop rotation. |
| Apple orchard | Soil Bench; Farm Scout; Pheno Tracker | Soil and scouting with optional fruit-trait scoring. |
| Berry farm | Soil Bench; Farm Scout; Pheno Tracker | Soil, scouting, and optional fruit-trait scoring. |
| Nursery farm | Soil Bench; Farm Scout; Greenhouse Monitor; CP Bench (carnivorous plants) | Growing media, scouting, protected environment; CP Bench is a specialist reference only when relevant to the nursery's plants. |
| Livestock farm | Ethogram | Structured animal behavior observations; no herd, treatment, or veterinary workflow is inferred. |
| Poultry & eggs | Ethogram | Behavior observations remain separate from egg collections. |
| Dairy farm | Ethogram | Behavior observations remain separate from milk collections. |
| Vineyard farm | Soil Bench; Farm Scout; Pheno Tracker | Soil, scouting, and optional selection traits for vines. |
| Hay & forage | Soil Bench; Farm Scout | Soil and field scouting; no feed ration or forage grade calculation. |
| Hops farm | Soil Bench; Farm Scout; Pheno Tracker | Soil, scouting, and optional selection traits for hops. |
| Grains farm | Soil Bench; Farm Scout | Soil, seed germination, and rotation observations. |
| Seed farm | Soil Bench; Farm Scout; Breeding Bench; Pheno Tracker | Field and seed observations plus optional breeding and selection analysis. No seed certification follows from a tool result. |
| Tree nut orchard | Soil Bench; Farm Scout | Soil and scouting; no pollinizer compatibility claim. |
| Vegetable farm | Soil Bench; Farm Scout; Breeding Bench; Pheno Tracker | Soil, scouting, rotation, seed, and optional breeding/selection work. |
| Aquaculture farm | Food Web Builder | An optional ecosystem study, not production water-quality monitoring or harvest authorization. |
| Christmas tree farm | Soil Bench; Farm Scout | Soil and tree-block scouting; no harvest grade or sales workflow. |
| Apiary farm | None by default | Farm Scout's pollinator count does not assess a managed hive; use Hive checks for colony observations. |
| Mushroom farm | Collection Sheet; Fungal Characters | Wild macrofungal collection and character references only. They do not identify a cultivated batch as edible or replace batch records. |
| Cranberry farm | Soil Bench; Farm Scout | Soil and field scouting; no flood, harvest, or packout calculation. |
| Pumpkin patch | Soil Bench; Farm Scout | Soil, pest, pollinator, germination, and rotation observations; no visitor booking or sales posting. |
| Custom farm | None by default | A custom farm may represent any operation; no crop-specific science tool is presumed. |

The app's cards are entry points, not endorsements of every calculation for
every crop. For example, **Farm Scout** asks the grower to supply an action
threshold. Its pest verdict is a comparison to that chosen value, not a
pesticide recommendation. **Greenhouse Monitor** uses a stated leaf-temperature
assumption and labels its target bands as grower convention. Its leaf VPD and
light calculations should not be relabeled as mushroom-room climate advice.

## What was brought over

The table points to the exact CSRBT source page for each card. These pages are
the original interactive instruments, copied at the pinned commit; the
FlowersForever tab adds farm-specific discovery and a local-browser launcher.

| Card | CSRBT source and meaning | Boundary to keep visible |
|---|---|---|
| Soil Recipes | [Grower's Soil Recipes](https://github.com/RicheyWorks/CSRBT/blob/5990b9c21e419f5836c9f0a22f15d993403557a3/docs/soil-recipes.html) scales four separately sourced grower mixes. [Subcool’s original Super Soil article](https://hightimes.com/news/subcools-super-soil-step-by-step/) describes a cannabis container mix. | Subcool is the grower's name, not a CSRBT product. Keep the author's source, original units and ranges, and the distinction between a concentrate and plain base soil. The page does not calculate NPK, C:N, or yield from a published recipe. |
| Soil Bench | [Soil Bench](https://github.com/RicheyWorks/CSRBT/blob/5990b9c21e419f5836c9f0a22f15d993403557a3/docs/soil-bench.html) covers compost logs, dry-mass C:N, mix parts, and texture by feel. | Compost time/temperature criteria are not proof of maturity or crop suitability. Mix characteristics are ordinal ranks, not lab measurements. |
| Farm Scout | [Farm Scout](https://github.com/RicheyWorks/CSRBT/blob/5990b9c21e419f5836c9f0a22f15d993403557a3/docs/farm-scout.html) covers pest counts, pollinator counts, germination tests, and plant-family rotation checks. | Its threshold is chosen by the user; do not turn its comparison into a universal treatment instruction. |
| Greenhouse Monitor | [Greenhouse Monitor](https://github.com/RicheyWorks/CSRBT/blob/5990b9c21e419f5836c9f0a22f15d993403557a3/docs/greenhouse.html) reads environment, light, and energy logs. | Leaf VPD depends on the leaf-temperature assumption; DLI from lux needs an explicit fixture factor. Run comparisons are observational. |
| Breeding Bench | [Breeding Bench](https://github.com/RicheyWorks/CSRBT/blob/5990b9c21e419f5836c9f0a22f15d993403557a3/docs/breeding-bench.html) covers population, isolation, trials, and seed germination. | Its crop picker does not include cannabis; generic analysis can still be explored, but do not imply cannabis-specific population or isolation guidance. Most isolation distances are not supplied; planning notes are not seed certification. |
| Pheno Tracker | [Pheno Tracker](https://github.com/RicheyWorks/CSRBT/blob/5990b9c21e419f5836c9f0a22f15d993403557a3/docs/pheno-tracker.html) scores user-defined traits and records selection runs. | Trait weights and scores are the grower's choices, not measured genetic performance. |
| Relevé | [Relevé](https://github.com/RicheyWorks/CSRBT/blob/5990b9c21e419f5836c9f0a22f15d993403557a3/docs/releve.html) records vegetation plots and botanical vouchers. | Its plot observations do not create crop plans, seed lots, or nursery inventory. |
| Collection Sheet; Fungal Characters | [Collection Sheet](https://github.com/RicheyWorks/CSRBT/blob/5990b9c21e419f5836c9f0a22f15d993403557a3/docs/collection-sheet.html) and [Fungal Characters](https://github.com/RicheyWorks/CSRBT/blob/5990b9c21e419f5836c9f0a22f15d993403557a3/docs/fungal-characters.html) support wild specimen descriptions. | Neither says whether a fungus is edible. They are separate from cultivated mushroom batches. |
| Ethogram | [Ethogram](https://github.com/RicheyWorks/CSRBT/blob/5990b9c21e419f5836c9f0a22f15d993403557a3/docs/ethogram.html) structures observed animal states and events. | It does not diagnose animal health or write herd, egg, or milk records. |
| CP Bench | [CP Bench](https://github.com/RicheyWorks/CSRBT/blob/5990b9c21e419f5836c9f0a22f15d993403557a3/docs/cp-bench.html) handles carnivorous-plant water, media, plant, and dormancy logs. | Only relevant to a nursery growing those plants; its nutrient-free media guidance is not a general nursery rule. |
| Food Web Builder | [Food Web Builder](https://github.com/RicheyWorks/CSRBT/blob/5990b9c21e419f5836c9f0a22f15d993403557a3/docs/food-web.html) explores feeding links and simulated removals. | It is an ecology model, not aquaculture feed management or water-quality testing. |

CSRBT's [soil-recipe decision record](https://github.com/RicheyWorks/CSRBT/blob/5990b9c21e419f5836c9f0a22f15d993403557a3/docs/ADR-042-grower-soil-recipes-2026-08-26.md)
explains why recipe amounts are left in their source units, published ranges
stay ranges, and different recipes are not normalized to an invented common
batch size. Its [evidence rule](https://github.com/RicheyWorks/CSRBT/blob/5990b9c21e419f5836c9f0a22f15d993403557a3/docs/ADR-031-entry-layer-and-suites-2026-08-24.md)
distinguishes a sourced result, a labeled convention, and a result the kit
refuses to claim. Those source boundaries remain part of this port.

For the four recipe cards, CSRBT links to [Subcool in *High Times*](https://hightimes.com/news/subcools-super-soil-step-by-step/),
[BuildASoil's discussion of The Rev's TLO mix](https://buildasoil.com/blogs/news/9885098-why-tlo-dissecting-the-rev-mix-line-by-line),
[Growing Organic's Coot mix reference](https://growingorganic.com/soil-guide/coots-soil-mix/),
and [BuildASoil's own living-soil recipe](https://buildasoil.com/blogs/news/living-soil-ingredients-and-recipe).
The Rev and Coot links are references used by CSRBT, not claims that those
pages are the growers' original publications.

## Completeness audit and live data

The [October 10 audit](CSRBT_COMPLETENESS_AUDIT_20261010.md) inventories every upstream page and separately identifies Java engines and integration gaps. The [farm readings contract](FARM_READINGS.md) describes the first persistent data intake boundary. Browser-tool exports and incoming sensor readings remain distinct records.
