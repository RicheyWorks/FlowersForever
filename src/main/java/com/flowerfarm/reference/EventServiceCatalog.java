package com.flowerfarm.reference;

import com.flowerfarm.model.EventServiceProfile;
import com.flowerfarm.model.EventServiceProfile.Source;

import java.util.Comparator;
import java.util.List;

/** Bundled planning starters synthesized from current event-industry workflows. */
public final class EventServiceCatalog {

    private static final Source CURATE = source(
            "Curate",
            "AI-Assisted Operations Platform for Event Businesses",
            "https://curate.co/");
    private static final Source CURATE_FEATURES = source(
            "Curate",
            "Event management platform features",
            "https://curate.co/features/");
    private static final Source FLORANEXT = source(
            "Floranext",
            "Creating a wedding or event proposal",
            "https://support.floranext.com/kb/article/110-creating-a-proposal/");
    private static final Source TRIPLESEAT = source(
            "Tripleseat",
            "Event venue booking, reservation, and sales software",
            "https://tripleseat.com/platform/book/");
    private static final Source TRIPLESEAT_PORTAL = source(
            "Tripleseat",
            "Event venue guest portal and planning software",
            "https://tripleseat.com/features/guest-portal/");
    private static final Source HONEYBOOK = source(
            "HoneyBook",
            "Florist client-management software",
            "https://www.honeybook.com/software-for-florists");
    private static final Source TRIBUTE_VIDEO = source(
            "Tribute Technology",
            "Tribute Video",
            "https://www.tributetech.com/tribute-video");
    private static final Source TRIBUTE_MANAGEMENT = source(
            "Tribute Technology",
            "Tribute Management Software",
            "https://www.tributetech.com/tribute-management-software");
    private static final Source TRIBUTE_APPROVALS = source(
            "Tribute Technology",
            "Obituary photo and video approval settings",
            "https://support.tributetech.com/tributecenteronline/obituary-settings");
    private static final Source NFDA = source(
            "National Funeral Directors Association",
            "Remembering A Life idea guide for funeral professionals",
            "https://nfda.org/Portals/0/NFDAORG/Resources/PublicCommunityRelations/"
                    + "RememberingaLife/2024%20Idea%20Guide%20Funeral%20Professionals%20WEB.pdf");

    private static final List<EventServiceProfile> SERVICES = List.of(
            service("Personal flowers", "Wedding", "Floral design", "Per piece / package",
                    "Consult early; finalize after attire and party count",
                    "Bouquets, boutonnieres, corsages, flower-person pieces, labeled handoff",
                    "Wedding party count, attire colors, allergies, keepsake wishes, photo time",
                    "Recipe each piece; label by person; hydrate; stage a repair kit; confirm handoff",
                    "Confirm allergies and pin/magnet needs; get approval before portfolio use",
                    FLORANEXT),
            service("Ceremony flowers", "Wedding", "Floral design", "Per installation",
                    "Venue walkthrough before final design",
                    "Aisle markers, altar pieces, arch flowers, petals, transfer or strike plan",
                    "Ceremony location, dimensions, weather backup, aisle length, reuse plan",
                    "Measure; recipe; mechanics review; load-in; installation photo; strike or transfer",
                    "Follow venue attachment, flame, aisle-clearance, and outdoor-weather rules",
                    CURATE),
            service("Reception centerpieces", "Wedding", "Floral design", "Per table / piece",
                    "Finalize with floor plan and table count",
                    "Guest-table, head-table, welcome, bar, cake, and remembrance arrangements",
                    "Table sizes, counts, linens, sightlines, candles, vessels, repurpose plan",
                    "Match counts to floor plan; recipe; label zones; place; photograph; recover rentals",
                    "Keep exits and food service clear; document rental ownership and breakage terms",
                    CURATE_FEATURES),
            service("Wedding floral proposal", "Wedding", "Planning & coordination", "Per event",
                    "Prepare after discovery; revise before purchasing",
                    "Visual scope, floral categories, inspiration references, quantities, labor, terms",
                    "Budget range, priorities, palette, substitutions, locations, decision maker",
                    "Record revisions; capture approval; lock recipes; create purchase and production lists",
                    "Use only client-authorized inspiration images and clarify seasonal substitutions",
                    FLORANEXT),
            service("Reception catering", "Wedding", "Catering", "Per guest / minimum",
                    "Menu and service plan before venue deadline",
                    "Menu, staffing, service style, beverage plan, rentals, setup and cleanup",
                    "Guest count, dietary needs, allergies, kitchen access, service timing, budget",
                    "Confirm licensed provider; menu; counts; floor plan; rentals; BEO-style run sheet",
                    "Route allergy decisions to the caterer; verify permits, insurance, and food safety",
                    TRIPLESEAT),
            service("Dessert, coffee, and late-night service", "Wedding", "Catering",
                    "Per guest / station", "Finalize quantities with attendance count",
                    "Dessert display, cake service, coffee station, late-night snack, cleanup",
                    "Cake handling, refrigeration, allergens, serviceware, power, replenishment",
                    "Assign receiving contact; confirm display time; protect florals from food contact",
                    "Licensed food provider owns storage and allergen controls; note open-flame limits",
                    TRIPLESEAT_PORTAL),
            service("Table décor and linens", "Wedding", "Décor & rentals", "Per table / rental",
                    "Reserve after floor plan; recount before event",
                    "Linens, runners, candles, place-setting accents, signage, table-number styling",
                    "Table dimensions, palette, candle rules, delivery window, return condition",
                    "Count out; label by room; photograph setup; reconcile returns and damage",
                    "Obtain venue approval for flame, adhesives, hanging, and electrical items",
                    CURATE_FEATURES),
            service("Arch, backdrop, and installation rentals", "Wedding", "Décor & rentals",
                    "Per structure / event", "Measure and reserve well before event",
                    "Structure, weights, floral attachments, delivery, assembly, strike",
                    "Dimensions, surface, wind exposure, access, load limits, relocation, strike time",
                    "Inspect; photograph condition; pack hardware; safety-check; install; recover",
                    "Use rated hardware and ballast; do not block egress; follow venue engineering rules",
                    CURATE),
            service("Wedding photography coverage", "Wedding", "Photography & media",
                    "Per hour / package", "Book early; lock timeline before event",
                    "Coverage hours, lead photographer, second shooter option, edited gallery, delivery date",
                    "Locations, timeline, must-have people, private moments, image style, usage rights",
                    "Shot list; vendor timeline; contact sheet; backups; preview; final delivery tracking",
                    "Record portfolio/social consent separately from event coverage and respect opt-outs",
                    HONEYBOOK),
            service("Floral and décor detail photography", "Wedding", "Photography & media",
                    "Per event / add-on", "Schedule before guests enter each space",
                    "Installation, bouquet, centerpiece, signage, room-wide, and vendor-credit images",
                    "Which work needs documentation, credit list, clean-room window, usage permissions",
                    "Coordinate setup completion; capture condition; store filenames with event record",
                    "Do not photograph people or private materials without permission; agree vendor credits",
                    FLORANEXT),
            service("Wedding delivery, setup, and strike", "Wedding", "Logistics",
                    "Per route / labor hour", "Confirm windows with every location",
                    "Load plan, delivery route, room placement, installation crew, transfers, strike",
                    "Addresses, access codes, loading rules, stairs, parking, contacts, weather backup",
                    "Load by stop; count; arrival check-in; setup photos; transfer; strike; rental count",
                    "Set realistic lifting and driving plans; protect venues and document preexisting damage",
                    CURATE),
            service("Wedding vendor coordination", "Wedding", "Planning & coordination",
                    "Per event / hour", "Begin with signed scopes and a shared timeline",
                    "Vendor roster, timeline, confirmations, setup ownership, escalation contacts",
                    "Who decides changes, vendor contracts, access windows, payment status, backups",
                    "Publish current timeline; confirm each vendor; run check-ins; log changes and approvals",
                    "Keep private client details on a need-to-know basis and document scope changes",
                    HONEYBOOK),

            service("Casket spray or urn surround", "Memorial / funeral", "Floral design",
                    "Per piece", "Coordinate promptly with funeral-home schedule",
                    "Casket spray, urn surround, transport mechanics, placement and family handoff",
                    "Casket or urn dimensions, open/closed service, palette, meaningful flowers, location",
                    "Confirm dimensions with funeral home; recipe; secure mechanics; delivery check-in",
                    "Use calm, family-led language; do not publish names or images without permission",
                    TRIBUTE_MANAGEMENT),
            service("Standing tribute and easel pieces", "Memorial / funeral", "Floral design",
                    "Per piece / rental", "Confirm venue and service times before production",
                    "Wreaths, hearts, crosses or custom tributes, easels, cards, placement plan",
                    "Traditions, symbols, wording, colors, sender, venue rules, destination afterward",
                    "Label sender and destination; verify easel stability; place; recover farm-owned easels",
                    "Follow family preferences for faith and cultural symbols; never infer them",
                    NFDA),
            service("Family sympathy arrangements", "Memorial / funeral", "Floral design",
                    "Per arrangement", "Confirm recipient and delivery window",
                    "Vase, basket, plant, or low arrangement with card and delivery record",
                    "Recipient, relationship, address, card text, pets, scent or pollen concerns",
                    "Read back card; verify address; photo only if requested; record delivery contact",
                    "Treat names, addresses, and service details as private; verify before sharing",
                    HONEYBOOK),
            service("Memorial reception catering", "Memorial / funeral", "Catering",
                    "Per guest / minimum", "Plan around service and interment timing",
                    "Meal or buffet, staffing, beverage service, rentals, setup, replenishment, cleanup",
                    "Attendance range, dietary needs, allergies, venue kitchen, service timing, leftovers",
                    "Confirm provider; menu; counts; room layout; receiving contact; run sheet; cleanup",
                    "Licensed caterer owns food safety and allergy controls; keep the plan easy to revise",
                    TRIPLESEAT),
            service("Coffee and refreshment station", "Memorial / funeral", "Catering",
                    "Per guest / station", "Confirm access and replenishment window",
                    "Coffee, tea, water, light refreshments, serviceware, waste and cleanup",
                    "Guest estimate, duration, dietary needs, power, water, volunteers or staff",
                    "Stage quietly; label allergens; assign replenishment; remove without disrupting service",
                    "Use a licensed provider where required and follow venue food-service rules",
                    NFDA),
            service("Remembrance table and guestbook décor", "Memorial / funeral",
                    "Décor & rentals", "Per display", "Coordinate after family selects keepsakes",
                    "Table styling, cloth, florals, framed-photo placements, guestbook, memory-card station",
                    "Which objects are approved, who transports them, photo sizes, wording, return contact",
                    "Inventory every keepsake; photograph layout for staff; assign secure return handoff",
                    "Family retains control of personal objects and photos; no public sharing by default",
                    NFDA),
            service("Graveside floral and seating setup", "Memorial / funeral", "Décor & rentals",
                    "Per service / rental", "Confirm cemetery rules and weather plan",
                    "Floral placement, easels, limited seating, weather protection, removal plan",
                    "Cemetery contact, arrival route, ground conditions, attendance, mobility needs",
                    "Confirm permission; secure pieces; protect paths; remove farm equipment promptly",
                    "Follow cemetery rules and accessibility needs; do not leave non-approved materials",
                    TRIBUTE_MANAGEMENT),
            service("Memorial photography", "Memorial / funeral", "Photography & media",
                    "Per hour / package", "Offer only after explicit family and venue approval",
                    "Discreet coverage, agreed moments, private gallery, delivery and retention plan",
                    "Who may authorize, what may be photographed, excluded people or moments, recipients",
                    "Document consent; brief photographer; minimize presence; preview; deliver privately",
                    "Written family decision-maker permission and venue policy come first; no portfolio use",
                    TRIBUTE_APPROVALS),
            service("Tribute photo and video coordination", "Memorial / funeral",
                    "Photography & media", "Per tribute / package", "Set upload and approval deadlines",
                    "Family upload link, photo/video collection, theme, music rights, preview, approved export",
                    "Family approver, desired length, captions, order, private images, display equipment",
                    "Gather; deduplicate; verify rights; build; send private preview; record approval; deliver",
                    "Use family-provided or licensed media; moderate submissions and require final approval",
                    TRIBUTE_VIDEO),
            service("Memorial delivery, setup, and pickup", "Memorial / funeral", "Logistics",
                    "Per route / labor hour", "Coordinate with funeral home, venue, and cemetery",
                    "Multi-stop route, floral placement, easels, remembrance display, recovery and returns",
                    "Service, visitation and interment times, contacts, entrances, labels, final destinations",
                    "Load by stop; check in; place quietly; confirm family pieces; recover rentals",
                    "Protect confidential service details and avoid interrupting family or officiants",
                    TRIBUTE_MANAGEMENT),
            service("Funeral-home and family liaison", "Memorial / funeral",
                    "Planning & coordination", "Per service / hour", "Begin as soon as scope is accepted",
                    "Single contact list, service timeline, approvals, vendor handoffs, change log",
                    "Family decision maker, funeral director, officiant, venues, cultural needs, deadlines",
                    "Confirm details once; publish current plan; route changes through named approver",
                    "Minimize repeated questions to the family; share sensitive details only as needed",
                    TRIBUTE_MANAGEMENT),

            service("Venue walkthrough and production plan", "Any event",
                    "Planning & coordination", "Per visit / event",
                    "Complete before design and staffing are locked",
                    "Measurements, access map, floor plan notes, load-in/out windows, power and weather plan",
                    "Contacts, capacity, rules, insurance, parking, accessibility, kitchen, storage, strike",
                    "Photograph approved work areas; measure; assign owners; publish setup and strike plan",
                    "Get venue permission before photos; record emergency exits without obstructing them",
                    TRIPLESEAT_PORTAL),
            service("Rental and return control", "Any event", "Décor & rentals",
                    "Per event / item", "Reserve before client approval deadline",
                    "Rental counts, condition record, vendor ownership, delivery, placement, return",
                    "Quantities, substitutions, damage terms, return deadline, cleaning, responsible contact",
                    "Count out; label; photograph condition; reconcile after strike; record missing items",
                    "Separate farm-owned stock from third-party rentals and document damage fairly",
                    CURATE_FEATURES),
            service("Client approval and payment schedule", "Any event",
                    "Planning & coordination", "Per event", "Set before purchasing or reserving",
                    "Scope approval, contract checkpoint, deposit dates, final balance, change approvals",
                    "Decision maker, billing contact, cancellation terms, revision limit, payment method",
                    "Record current version; obtain approval; schedule reminders; log paid and due amounts",
                    "Have contracts reviewed for the business and jurisdiction; never imply legal advice",
                    HONEYBOOK)
    );

    private EventServiceCatalog() {
    }

    public static List<EventServiceProfile> all() {
        return SERVICES;
    }

    public static List<String> occasions() {
        return SERVICES.stream().map(EventServiceProfile::occasion).distinct().sorted().toList();
    }

    public static List<String> categories() {
        return SERVICES.stream().map(EventServiceProfile::category).distinct().sorted().toList();
    }

    public static List<Source> sources() {
        return SERVICES.stream().map(EventServiceProfile::source).distinct()
                .sorted(Comparator.comparing(Source::organization)
                        .thenComparing(Source::title))
                .toList();
    }

    private static EventServiceProfile service(
            String name, String occasion, String category, String pricingBasis,
            String leadTime, String deliverables, String questions, String checklist,
            String privacy, Source source) {
        return new EventServiceProfile(name, occasion, category, pricingBasis, leadTime,
                deliverables, questions, checklist, privacy, source);
    }

    private static Source source(String organization, String title, String url) {
        return new Source(organization, title, url);
    }
}
