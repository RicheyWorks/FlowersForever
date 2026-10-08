package com.flowerfarm.scouting;

import java.util.List;
import java.util.Optional;

/**
 * "How to send a sample" guides shown in the app. Steps come from the lab and
 * extension pages listed with each guide (retrieved Oct 7, 2026; docs/LAB-SAMPLES.md).
 */
public final class SampleGuides {

    /** One guide: title, numbered steps, and where they come from. */
    public record Guide(String id, String title, Scouting.SampleType type, List<String> steps,
                        List<String> sources) {

        @Override
        public String toString() {
            return title;
        }

        /** Plain text for the in-app viewer and the printable sheet. */
        public String asText() {
            StringBuilder text = new StringBuilder(title).append("\n\n");
            for (int i = 0; i < steps.size(); i++) {
                text.append(i + 1).append(". ").append(steps.get(i)).append('\n');
            }
            text.append("\nSources (checked ").append(LabDirectory.CHECKED).append("):\n");
            sources.forEach(source -> text.append("- ").append(source).append('\n'));
            return text.toString();
        }
    }

    private static final List<Guide> GUIDES = List.of(
            new Guide("plant-disease", "Sending a diseased plant", Scouting.SampleType.PLANT_DISEASE, List.of(
                    "Call or email the lab first if you are unsure which form to use. WSU Puyallup asks commercial "
                            + "growers to use form C0084 and to sign it; unsigned forms are not processed.",
                    "Pick plants that show the problem at different stages, especially early stages. Include a "
                            + "healthy plant, an early one and a badly affected one if you can. Do not send dead, "
                            + "dry or rotted plants.",
                    "For wilting, yellowing or dieback, dig up the whole plant with its roots and some soil. Dig, "
                            + "don't pull: pulling can leave diseased roots behind.",
                    "For leaves, flowers or stems, send pieces that show both diseased and healthy tissue (the "
                            + "edge between them).",
                    "Wrap the roots and soil in a plastic bag so soil stays off the leaves, then put the whole "
                            + "plant in a second plastic bag. Do not add water. Wrap fleshy parts (bulbs, tubers, "
                            + "corms) in dry paper towels or newspaper.",
                    "Fill in the form completely: crop and variety, when you first saw the problem, how fast it "
                            + "spreads, how many plants, site and watering, and any sprays or fertilizer (product, "
                            + "rate, date). Put the form in an envelope on the outside of the box.",
                    "Pack in a sturdy box with padding. Ship early in the week; do not mail on a Friday or before a "
                            + "holiday. You can also drop off at WSU Puyallup Mon-Fri 9-12 and 1-3.",
                    "Until it goes out, keep the bagged sample in a refrigerator so it stays fresh. Ask the lab "
                            + "before adding a cold pack in hot weather. Photos can be emailed to the lab as well."),
                    List.of("https://puyallup.wsu.edu/plantclinic/plant-problems/",
                            "https://wpcdn.web.wsu.edu/wp-puyallup/uploads/sites/408/2015/02/c0084.pdf",
                            "https://wpcdn.web.wsu.edu/extension/uploads/sites/75/2021/05/"
                                    + "Preparing-Samples-for-Identification-or-Diagnosis.docx.pdf")),
            new Guide("tape-lift", "Tape lifts and swabs", Scouting.SampleType.PLANT_DISEASE, List.of(
                    "None of the Washington labs in this directory list tape lifts or swabs as a way to submit plant "
                            + "disease samples. They ask for fresh plant tissue instead.",
                    "Ask the lab before sending a tape lift or swab, and send fresh tissue as well.",
                    "WSU Puyallup says not to send insects stuck on tape or sticky traps: the sticky surface ruins "
                            + "the features used to identify them."),
                    List.of("https://puyallup.wsu.edu/plantclinic/plant-problems/",
                            "https://puyallup.wsu.edu/plantclinic/insect-identification/")),
            new Guide("insect", "Sending insects and mites", Scouting.SampleType.INSECT, List.of(
                    "Send several intact insects, not crushed ones, and a piece of the damaged plant if you like.",
                    "For mailing to WSU Puyallup: kill the insects by freezing them overnight, then pack them "
                            + "gently with tissue or cotton in a stoppered vial, and put the vial in a crush-proof "
                            + "box. Use form C0495 and sign it.",
                    "Do not put butterflies, moths or bees in alcohol; send them dead and dry in a crush-proof "
                            + "container.",
                    "Soft-bodied insects and spiders keep best in 70% alcohol, but alcohol is a flammable liquid "
                            + "and cannot be mailed. Drop alcohol vials off in person, or ask the lab how to ship.",
                    "Do not send live insects, crushed insects, or insects on tape or sticky traps.",
                    "For a possible invasive pest, email clear photos from several angles to WSDA at "
                            + "pest@agr.wa.gov first, keep the insect in a clear container, and wait for WSDA's "
                            + "instructions. WSDA asks for its Pest & Disease Sample Form with any specimen.",
                    "Do not mail on a Friday or before a holiday."),
                    List.of("https://puyallup.wsu.edu/plantclinic/insect-identification/",
                            "https://wpcdn.web.wsu.edu/wp-puyallup/uploads/sites/408/2015/02/c0495.pdf",
                            "https://puyallup.wsu.edu/plantclinic/faq/",
                            "https://cms.agr.wa.gov/WSDAKentico/Documents/Pubs/"
                                    + "AGR7-2504-030-InvasivePestDetectionBooklet.pdf")),
            new Guide("soil", "Taking a soil sample", Scouting.SampleType.SOIL, List.of(
                    "Pick the lab first and follow its instructions; use the same lab, depth and time of year in "
                            + "later years so results compare.",
                    "Sample each area separately (for example each block with a different crop or history).",
                    "Take 15-20 small subsamples across one area, using clean tools. Don't use galvanized, brass or "
                            + "bronze tools if you are testing micronutrients such as zinc.",
                    "Sample where the roots grow: for most crops from the surface to about 6 inches, or the depth "
                            + "of tillage. Keep the same depth every time.",
                    "Mix all subsamples from one area thoroughly in a clean bucket; crush clods and remove rocks, "
                            + "roots and debris.",
                    "If you are not testing for nitrogen, spread the soil on newspaper to air-dry before packing. "
                            + "If you are testing nitrogen, keep it cool and send it right away, early in the week.",
                    "Send about 1-2 cups per sample in a plastic zipper bag (Soiltest asks for about 2 cups; UMass "
                            + "for about 1 cup of dry soil). Label each bag, fill in the lab's form, and take a photo "
                            + "of the labeled bags."),
                    List.of("https://extension.oregonstate.edu/catalog/ec-628-guide-collecting-soil-samples-farms"
                                    + "-gardens",
                            "https://soiltestlab.com/info/residential/garden-lawn-testing/",
                            "https://www.umass.edu/agriculture-food-environment/services/soil-plant-nutrient"
                                    + "-testing-laboratory/ordering-information-forms",
                            "https://wpcdn.web.wsu.edu/wp-extension/uploads/sites/2093/2021/03/"
                                    + "SoilTestingHomeGardeners.pdf")),
            new Guide("water", "Testing irrigation and well water", Scouting.SampleType.WATER, List.of(
                    "Decide what you are testing. Irrigation suitability (pH, salts/EC, alkalinity, sodium/SAR, "
                            + "boron, chloride, nitrate) goes to an agricultural lab such as Soiltest, A&L Western or "
                            + "the University of Idaho. Well water safety (coliform bacteria and nitrate) goes to a "
                            + "Washington-accredited drinking water lab; the Department of Health recommends testing "
                            + "a private well every year for both.",
                    "Get the container from the lab, or use what it asks for: A&L wants a clean 16 oz plastic bottle "
                            + "for an irrigation test, and Soiltest supplies containers. Bacteria tests need the "
                            + "lab's own treated bottle. The University of Idaho asks you to call or email before "
                            + "sending water.",
                    "Check drop-off days and hold times before you sample. Soiltest takes water Monday-Thursday "
                            + "only; A&L wants it at the lab within 24-36 hours; bacteria samples have the shortest "
                            + "hold times (Lewis County's lab, for example, wants coliform samples the same day they "
                            + "are collected).",
                    "Run the water first: at least five minutes at the tap (Soiltest), or pump a well for at least "
                            + "10 minutes (A&L). Sample straight from the source, before filters or injectors. To "
                            + "check the system itself, sample before and after the injector and filter station, or at "
                            + "the end of the line.",
                    "From a pond or ditch, don't sample at the edge; it won't represent the whole water body.",
                    "For chemistry, rinse the bottle and cap three times with the water, then fill it completely "
                            + "with no air space and cap it tightly. For bacteria, do not rinse the bottle; just "
                            + "fill it as the lab directs.",
                    "Label each bottle with the well or source name (not just 1, 2, 3) and the date, and list the "
                            + "tests you want on the lab's form.",
                    "Keep the sample cool but don't freeze it. Pack the form (and payment, if you have no account) "
                            + "in the same box; samples sent separately from their paperwork are delayed.",
                    "Test irrigation water every year to spot trends; A&L notes the end of summer often shows the "
                            + "worst quality."),
                    List.of("https://soiltestlab.com/home/water-analysis/",
                            "https://soiltestlab.com/wp-content/uploads/2026/09/WaterPL.pdf",
                            "https://al-labs-west.com/water-analysis/",
                            "https://www.uidaho.edu/idaho-ag-experiment-station/services/analytical-sciences",
                            "https://doh.wa.gov/community-and-environment/drinking-water/contaminants/"
                                    + "testing-your-water",
                            "https://doh.wa.gov/sites/default/files/2026-03/331-798.pdf",
                            "https://ecology.wa.gov/water-shorelines/water-supply/wells/testing-drinking-water",
                            "https://lewiscountywa.gov/departments/environmental-health/water-laboratory/")));

    private SampleGuides() {
    }

    public static List<Guide> all() {
        return GUIDES;
    }

    public static Optional<Guide> byId(String id) {
        return GUIDES.stream().filter(guide -> guide.id().equals(id)).findFirst();
    }

    /** The first guide that fits a sample type. */
    public static Guide forType(Scouting.SampleType type) {
        return GUIDES.stream().filter(guide -> guide.type() == type).findFirst().orElse(GUIDES.get(0));
    }
}
