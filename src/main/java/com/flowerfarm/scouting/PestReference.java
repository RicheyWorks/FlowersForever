package com.flowerfarm.scouting;

import java.util.List;
import java.util.Optional;

/**
 * Starter reference of common cut-flower pests and diseases for Pacific Northwest
 * growers. Every entry is summarised from the extension page in {@code sourceUrl}
 * (retrieved Oct 7, 2026; see docs/SCOUTING.md). It is a scouting aid, not a diagnosis:
 * when in doubt, send a sample to a lab.
 */
public final class PestReference {

    /** One pest or disease with what to look for and how to scout it. */
    public record Entry(String id, String name, Scouting.Category category, String organism, String lookFor,
                        String scoutingTips, String sourceTitle, String sourceUrl) {

        @Override
        public String toString() {
            return name;
        }
    }

    private static final String PNW = "PNW Plant Disease Management Handbook: ";
    private static final String HORTSENSE = "WSU HortSense: ";

    private static final List<Entry> ENTRIES = List.of(
            new Entry("botrytis", "Botrytis gray mold", Scouting.Category.FUNGAL, "Botrytis cinerea",
                    "Water-soaked tan to brown spots, often on petals first; in humid weather a fuzzy gray-brown "
                            + "growth covers the spot. Blossom blight, leaf spots, and bud and stem rots. Stored "
                            + "stems, roots and corms in cold storage can also rot.",
                    "Check spent and fading blooms and dense, damp canopies after cool, wet spells. Remove dead "
                            + "and dying flowers and leaves from the bed. In tunnels or greenhouses keep humidity "
                            + "below 90%, space plants for air flow, and do not splash water on foliage.",
                    PNW + "Greenhouse Plants, Ornamental - Gray Mold",
                    "https://pnwhandbooks.org/plantdisease/host-disease/greenhouse-plants-ornamental-gray-mold"),
            new Entry("powdery-mildew", "Powdery mildew", Scouting.Category.FUNGAL,
                    "Several species (e.g. Golovinomyces, Podosphaera)",
                    "White to gray powdery growth on leaves, stems and flowers. Early colonies are faint: the upper "
                            + "leaf may show only pale spots while the underside has sparse white growth. Can be "
                            + "confused with spray residue. On dahlia, grayish white spots spread into a cobwebby mat.",
                    "Scout weekly. Look at four fully expanded leaves in the middle of 1 of every 30 plants; if you "
                            + "find it, check 1 of every 10. It is easier to see with the sun behind your shoulder. "
                            + "Favored by 60-80 °F, low light, cool nights and crowding. Remove infected parts and "
                            + "seal them in plastic.",
                    PNW + "Greenhouse Plants, Ornamental - Powdery Mildew; Dahlia - Powdery Mildew",
                    "https://pnwhandbooks.org/plantdisease/host-disease/greenhouse-plants-ornamental-powdery-mildew"),
            new Entry("downy-mildew", "Downy mildew (snapdragon)", Scouting.Category.FUNGAL,
                    "Peronospora antirrhini (a water mold, not a true fungus)",
                    "Seedling leaves curl in and droop; plants are stunted and pale yellow-green. Purple spots on "
                            + "dark cultivars, yellow spots on light ones. White to purplish growth on leaf "
                            + "undersides. Older plants may stop flowering and form rosettes.",
                    "Check leaf undersides, especially in cool, wet weather; leaves that stay wet 6-10 hours favor "
                            + "it. Space plants and reduce humidity by heating, venting and moving air.",
                    PNW + "Snapdragon (Antirrhinum majus) - Downy Mildew",
                    "https://pnwhandbooks.org/plantdisease/host-disease/snapdragon-antirrhinum-majus-downy-mildew"),
            new Entry("rust", "Rust (snapdragon)", Scouting.Category.FUNGAL, "Puccinia antirrhini",
                    "Pale or yellow spots on the upper leaf; underneath, rings of chocolate-brown pustules. "
                            + "Pustules also on stems and flowers. Plants look stunted and dry.",
                    "Turn leaves over. It needs 6-8 hours of leaf wetness and 55-70 °F, and is common on plants that "
                            + "overwintered. Avoid overhead watering, use clean cuttings, and choose more resistant "
                            + "cultivars.",
                    PNW + "Snapdragon (Antirrhinum majus) - Rust",
                    "https://pnwhandbooks.org/plantdisease/host-disease/snapdragon-antirrhinum-majus-rust"),
            new Entry("aphids", "Aphids", Scouting.Category.INSECT_MITE, "Many species",
                    "Clusters on tender new shoots, leaves and stems. Sticky, shiny honeydew, black sooty mold, "
                            + "ants tending colonies, and white cast skins. Some species distort leaves even in low "
                            + "numbers.",
                    "Check new growth and leaf undersides. Note lady beetles, lacewing larvae and syrphid fly "
                            + "maggots before treating. A strong stream of water, hand-wiping or pruning handles "
                            + "small colonies; avoid excess nitrogen.",
                    HORTSENSE + "Common Insects and Mites: Aphids",
                    "https://hortsense.cahnrs.wsu.edu/fact-sheet/common-insects-and-mites-aphids/"),
            new Entry("thrips", "Thrips (incl. western flower thrips)", Scouting.Category.INSECT_MITE,
                    "Several species; western flower thrips spreads INSV/TSWV",
                    "Tiny yellowish to dark insects feeding in flower and leaf buds. Distorted buds and leaves, "
                            + "brown spots on petals, buds that fail to open. Western flower thrips spread INSV and "
                            + "TSWV.",
                    "Hang light blue or yellow sticky traps just above the plants, about one per 500-1,000 sq ft; "
                            + "check them weekly and record where and when thrips are caught. Remove plant debris in "
                            + "fall to reduce overwintering adults.",
                    HORTSENSE + "Rose: Thrips; " + PNW + "Greenhouse Plants, Ornamental - Impatiens Necrotic Spot",
                    "https://hortsense.cahnrs.wsu.edu/fact-sheet/rose-thrips/"),
            new Entry("spider-mites", "Spider mites (incl. two-spotted spider mite)", Scouting.Category.INSECT_MITE,
                    "Several species",
                    "Very small eight-legged mites on leaf undersides. Fine yellow or bronze stippling, then overall "
                            + "yellowing or bronzing; silk webbing on undersides and in leaf axils.",
                    "Check leaf undersides in hot, dry weather; high leaf nitrogen and drought stress make outbreaks "
                            + "worse. Hose mites off, protect predatory mites and lady beetles, and avoid "
                            + "broad-spectrum insecticides.",
                    HORTSENSE + "Common Insects and Mites: Spider mites",
                    "https://hortsense.cahnrs.wsu.edu/fact-sheet/common-insects-mites-spider-mites/"),
            new Entry("cucumber-beetle", "Western spotted cucumber beetle", Scouting.Category.INSECT_MITE,
                    "Western spotted cucumber beetle",
                    "About 1/4 inch long with yellow wing covers and black spots; the rest of the body is black. "
                            + "Adults chew leaves and are often found in open flowers; larvae feed on roots.",
                    "Check leaves and open blooms. If you spray, do not spray blooming plants and spray in the "
                            + "evening after bees stop foraging.",
                    HORTSENSE + "Cucumber, Pumpkin, Squash: Western spotted cucumber beetle",
                    "https://hortsense.cahnrs.wsu.edu/fact-sheet/cucumber-pumpkin-squash-western-spotted-cucumber"
                            + "-beetle/"),
            new Entry("slugs", "Slugs", Scouting.Category.INSECT_MITE, "Several species (mollusks)",
                    "Ragged, chewed foliage; young plants eaten entirely; slime trails that dry silvery.",
                    "They feed at night and do the most damage in cool, moist weather, so check after dark or early "
                            + "morning. Clear weeds, boards and debris they hide under. Beer traps work; if you use "
                            + "bait, iron phosphate baits are safer for pets.",
                    HORTSENSE + "Cucumber, Pumpkin, Squash: Slugs",
                    "https://hortsense.cahnrs.wsu.edu/fact-sheet/cucumber-pumpkin-squash-slugs/"),
            new Entry("fusarium-wilt", "Fusarium wilt", Scouting.Category.FUNGAL,
                    "Fusarium oxysporum (crop-specific forms, e.g. f. sp. dianthi on carnation)",
                    "Lower leaves yellow, wilt and dry, often up one side of the plant, moving upward. The stem may "
                            + "shrivel and the water-conducting tissue inside turns brown. Shoots may bend.",
                    "Soilborne and long-lived in soil; spread by soil, water, infected cuttings and tools, and "
                            + "favored by warm conditions. Use clean stock and clean or soilless media. Wilts look "
                            + "alike, so confirm with a lab.",
                    PNW + "Carnation (Dianthus caryophyllus) - Fusarium Wilt",
                    "https://pnwhandbooks.org/plantdisease/host-disease/carnation-dianthus-caryophyllus-fusarium-wilt"),
            new Entry("verticillium-wilt", "Verticillium wilt", Scouting.Category.FUNGAL, "Verticillium dahliae",
                    "Leaves yellow and wilt, especially at the margins, then brown and die from the base of the plant "
                            + "upward. Brown streaks in stems. Plants are stunted and bloom poorly.",
                    "Soilborne and persistent. Note which beds wilt year after year. Take cuttings only from healthy "
                            + "plants; use clean or steamed media for transplants. Confirm with a lab.",
                    PNW + "Chrysanthemum - Verticillium Wilt",
                    "https://pnwhandbooks.org/plantdisease/host-disease/chrysanthemum-verticillium-wilt"),
            new Entry("damping-off", "Damping-off (Pythium, Rhizoctonia)", Scouting.Category.FUNGAL,
                    "Pythium spp. (water molds) and Rhizoctonia spp.",
                    "Poor emergence; seeds soft and rotting; seedlings collapse at the soil line. Pythium is favored "
                            + "by very wet, poorly drained media; Rhizoctonia by moderately moist media at 62-79 °F "
                            + "and attacks the stem base.",
                    "Check flats daily during emergence. Avoid overwatering and salt build-up, keep flats and benches "
                            + "clean. The causes are hard to tell apart by eye, so send seedlings to a plant clinic "
                            + "before choosing a treatment.",
                    PNW + "Greenhouse Plants, Ornamental - Pythium Seed Rot, Damping-off, and Root Rot; "
                            + "Rhizoctonia Damping-off, Root and Stem Rot",
                    "https://pnwhandbooks.org/plantdisease/host-disease/greenhouse-plants-ornamental-rhizoctonia"
                            + "-damping-root-stem-rot"),
            new Entry("insv-tswv", "INSV / TSWV (spread by thrips)", Scouting.Category.VIRAL,
                    "Impatiens necrotic spot virus, tomato spotted wilt virus",
                    "Dead spots and rings on leaves, dark areas along veins, yellowing, black or purple stem streaks, "
                            + "distorted or blackened young growth, stunting, and colored rings or streaks on petals. "
                            + "Can mimic other diseases, and may show no symptoms in warm, bright weather. Dahlias "
                            + "are commonly affected.",
                    "Have suspect plants tested (quick on-site test kits exist; labs can confirm). Remove infected "
                            + "plants with their soil, which holds thrips pupae. Monitor thrips with blue or yellow "
                            + "sticky traps. Petunia indicator plants can show the virus early.",
                    PNW + "Greenhouse Plants, Ornamental - Impatiens Necrotic Spot; Dahlia - Virus Diseases",
                    "https://pnwhandbooks.org/plantdisease/host-disease/greenhouse-plants-ornamental-impatiens"
                            + "-necrotic-spot"));

    private PestReference() {
    }

    public static List<Entry> all() {
        return ENTRIES;
    }

    public static Optional<Entry> byId(String id) {
        return ENTRIES.stream().filter(entry -> entry.id().equals(id)).findFirst();
    }

    /** Extra sources used for some entries (listed in docs/SCOUTING.md). */
    public static List<String> additionalSources() {
        return List.of(
                "https://pnwhandbooks.org/plantdisease/host-disease/dahlia-powdery-mildew",
                "https://pnwhandbooks.org/plantdisease/host-disease/dahlia-virus-diseases",
                "https://pnwhandbooks.org/node/2829/print");
    }
}
