package com.flowerfarm.scouting;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Washington lab and clinic directory for samples. Every detail was read from the
 * lab's own pages on {@link #CHECKED}; anything not stated there says "check with lab".
 * Fees change: the grower should confirm before sending. Sources: docs/LAB-SAMPLES.md.
 */
public final class LabDirectory {

    public static final LocalDate CHECKED = LocalDate.of(2026, 10, 7);
    public static final String CHECK_WITH_LAB = "Check with lab";

    /** One lab or clinic. */
    public record Lab(String id, String name, Set<Scouting.SampleType> accepts, String whatTheyDo,
                      String address, String phone, String email, String formUrl, String infoUrl, String fees,
                      String notes) {

        public boolean accepts(Scouting.SampleType type) {
            return accepts.contains(type);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static final List<Lab> LABS = List.of(
            new Lab("wsu-puyallup", "WSU Puyallup Plant & Insect Diagnostic Lab",
                    Set.of(Scouting.SampleType.PLANT_DISEASE, Scouting.SampleType.INSECT),
                    "Plant disease and disorder diagnosis, insect, mite and spider identification, and turf problems "
                            + "for western Washington.",
                    "WSU Puyallup Research & Extension Center, Plant & Insect Diagnostic Lab, 2606 W. Pioneer, "
                            + "Puyallup, WA 98371-4998",
                    "253-445-4582", "puy.plantdiagnostic@wsu.edu",
                    "https://wpcdn.web.wsu.edu/wp-puyallup/uploads/sites/408/2015/02/c0084.pdf",
                    "https://puyallup.wsu.edu/plantclinic/plant-problems/",
                    "Plant problem diagnosis $40; insect identification $25 base; information inquiry $25. Some tests "
                            + "cost extra. Cash or check payable to WSU Puyallup Plant Clinic (as listed Oct 7, 2026).",
                    "Commercial growers use form C0084 (plant disease); insects use form C0495: "
                            + "https://wpcdn.web.wsu.edu/wp-puyallup/uploads/sites/408/2015/02/c0495.pdf . The form "
                            + "must be signed. Do not mail on a Friday or before a holiday. Drop-off Mon-Fri 9-12 and "
                            + "1-3. Insects preserved in alcohol cannot be mailed; drop them off in person. The WSU "
                            + "Pullman clinic closed on Dec 1, 2025 and points growers here."),
            new Lab("wsda-entomology", "WSDA Plant Protection - Pest identification",
                    Set.of(Scouting.SampleType.INSECT),
                    "Identification of insects, mites and other arthropods, with a focus on invasive and "
                            + "quarantine pests.",
                    "WSDA, Attn: Entomology Lab, 1111 Washington St SE, PO Box 42560, Olympia, WA 98504 "
                            + "(check with WSDA before mailing)",
                    "360-902-2070", "pest@agr.wa.gov",
                    "https://cms.agr.wa.gov/WSDAKentico/Imported/PestAndDiseaseSampleForm.pdf",
                    "https://agr.wa.gov/departments/insects-pests-and-weeds/insects/exotic-pest-detection",
                    "No fee listed. " + CHECK_WITH_LAB + ".",
                    "Email clear photos from several angles to pest@agr.wa.gov first and keep the insect in a clear "
                            + "container until WSDA replies. Suspected invasive insects can also be reported through "
                            + "the Washington Invasive Species Council form or WA Invasives app. WSDA's form asks for "
                            + "70-80% alcohol vials; alcohol can't go through the mail, so ask WSDA how to send it."),
            new Lab("soiltest", "Soiltest Farm Consultants (Moses Lake)",
                    Set.of(Scouting.SampleType.SOIL, Scouting.SampleType.WATER),
                    "Soil, water, plant tissue (nutrient), feed and nematode analysis. Listed on WSU Extension's "
                            + "soil lab resources.",
                    "2925 Driggs Dr., Moses Lake, WA 98837", "1-800-764-1622", "",
                    "https://soiltestlab.com/wp-content/uploads/2024/12/GardenReqForm.pdf",
                    "https://soiltestlab.com/residential/garden-lawn-testing/",
                    "Lawn/garden form: S-14 basic $34, S-1 complete garden $49, S-7 complete for acid soils $61. "
                            + "Farm packages differ: " + CHECK_WITH_LAB + ".",
                    "Free request forms and sample bags by calling the toll-free number. Water analyses can take up "
                            + "to 14 days."),
            new Lab("al-western", "A&L Western Laboratories (Portland office)",
                    Set.of(Scouting.SampleType.SOIL, Scouting.SampleType.WATER),
                    "Soil, plant (nutrient), water, compost and manure analysis.",
                    "21830 SW Alexander Ln., Sherwood, OR 97140", "503-968-9225", "",
                    "https://al-labs-west.com/wp-content/uploads/2024/04/soil-al-form-4.5.24.pdf",
                    "https://al-labs-west.com/",
                    CHECK_WITH_LAB + " (no fee list on the website).",
                    "Choose a test package on the soil submission form."),
            new Lab("umass-soil", "UMass Soil & Plant Nutrient Testing Lab",
                    Set.of(Scouting.SampleType.SOIL),
                    "Routine soil analysis with lime and nutrient recommendations. Named by WSU Extension as accurate "
                            + "for western Washington soils.",
                    "Paige Laboratory, Room 203, 161 Holdsworth Way, Amherst, MA 01003", "413-545-2311", "",
                    "https://www.umass.edu/agriculture-food-environment/services/soil-plant-nutrient-testing"
                            + "-laboratory/ordering-information-forms",
                    "https://www.umass.edu/agriculture-food-environment/services/soil-plant-nutrient-testing"
                            + "-laboratory/lab-services",
                    "Routine soil analysis $20 per sample; optional organic matter $6, soluble salts $6, nitrate $8 "
                            + "(home grounds form). Check payable to UMass.",
                    "Pick the commercial or home form on the ordering page. Send about one cup of dry soil."),
            new Lab("pierce-mg", "Pierce County Master Gardener Answer Clinics (WSU Extension)",
                    Set.of(Scouting.SampleType.PLANT_DISEASE, Scouting.SampleType.INSECT),
                    "Free help from trained volunteers; samples accepted at community clinics.",
                    "Community clinics across Pierce County, e.g. Sehmel Park Demo Garden, 10123 78th Ave NW, Gig "
                            + "Harbor, WA 98332 (Wednesdays year-round, 9-11:30 am)",
                    "253-798-7170", "",
                    "https://extension.wsu.edu/pierce/mg-home/educational-outreach/ask-a-master-gardener/",
                    "https://extension.wsu.edu/pierce/mg-home/educational-outreach/ask-a-master-gardener/answer"
                            + "-clinics/",
                    "Free of charge.",
                    "Most community clinics run April-September; the email clinic runs year-round. Call before "
                            + "bringing samples to the office clinic (appointment only)."),
            new Lab("kitsap-mg", "Kitsap County Master Gardener Clinic (WSU Extension)",
                    Set.of(Scouting.SampleType.PLANT_DISEASE, Scouting.SampleType.INSECT),
                    "Trained volunteers look at samples and answer questions.",
                    "WSU Kitsap Extension Office, Norm Dicks Government Building, 345 6th St., Suite 550, Bremerton, "
                            + "WA 98337",
                    "360-228-7320 (hotline during clinic hours)", "",
                    "https://extension.wsu.edu/kitsap/master-gardeners/ask-a-master-gardener/",
                    "https://extension.wsu.edu/kitsap/master-gardeners/ask-a-master-gardener/answer-clinics/",
                    "No fee listed. " + CHECK_WITH_LAB + ".",
                    "Office clinic Wednesdays 10-12 and 1-3, April-October; drop samples off Wednesdays 10-3. "
                            + "Farmers market clinics (e.g. Port Orchard, Saturdays 9-2) run about mid-April to "
                            + "September."));

    private LabDirectory() {
    }

    public static List<Lab> all() {
        return LABS;
    }

    public static Optional<Lab> byId(String id) {
        return LABS.stream().filter(lab -> lab.id().equals(id)).findFirst();
    }

    public static List<Lab> accepting(Scouting.SampleType type) {
        return LABS.stream().filter(lab -> lab.accepts(type)).toList();
    }
}
