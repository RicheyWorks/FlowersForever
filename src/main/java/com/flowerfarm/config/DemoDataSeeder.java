package com.flowerfarm.config;

import com.flowerfarm.model.Customer;
import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.model.KnowledgeConfidence;
import com.flowerfarm.model.KnowledgeEntry;
import com.flowerfarm.model.KnowledgeKind;
import com.flowerfarm.model.OrderLine;
import com.flowerfarm.model.SeasonPlanEntry;
import com.flowerfarm.model.SeasonPlanStatus;
import com.flowerfarm.service.CustomerService;
import com.flowerfarm.service.HarvestService;
import com.flowerfarm.service.KnowledgeService;
import com.flowerfarm.service.OrderService;
import com.flowerfarm.service.SeasonPlanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

/**
 * Portfolio / show-and-tell seed data for empty CRM + harvest (profile {@code demo}).
 *
 * <p>Does not wipe existing data. Skips sections that already have rows so a
 * second launch on the same H2 file is a no-op.
 */
@Component
@Profile("demo")
@Order(50)
@ConditionalOnProperty(name = "flowerfarm.demo.seed", havingValue = "true", matchIfMissing = true)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final ZoneId PNW = ZoneId.of("America/Los_Angeles");

    private final CustomerService customerService;
    private final HarvestService harvestService;
    private final OrderService orderService;
    private final KnowledgeService knowledgeService;
    private final SeasonPlanService seasonPlanService;

    public DemoDataSeeder(CustomerService customerService,
                          HarvestService harvestService,
                          OrderService orderService,
                          KnowledgeService knowledgeService,
                          SeasonPlanService seasonPlanService) {
        this.customerService = customerService;
        this.harvestService = harvestService;
        this.orderService = orderService;
        this.knowledgeService = knowledgeService;
        this.seasonPlanService = seasonPlanService;
    }

    @Override
    public void run(ApplicationArguments args) {
        LocalDate today = LocalDate.now(PNW);
        int customers = seedCustomersIfEmpty();
        int harvests = seedHarvestsIfEmpty(today);
        int orders = seedOrdersIfEmpty(today);
        int knowledge = seedKnowledgeIfEmpty(today);
        int seasonPlans = seedSeasonPlanIfEmpty(today);
        log.info("Demo seed complete — customers+{}, harvests+{}, orders+{}, knowledge+{}, "
                        + "season plans+{} (today={})",
                customers, harvests, orders, knowledge, seasonPlans, today);
        if (customers + harvests + orders + knowledge + seasonPlans == 0) {
            log.info("Demo seed: nothing to add (farm workflows already populated). "
                    + "Use a fresh ./data folder for a clean demo.");
        } else {
            log.info("Demo ready: open Market Day, Season Planner, Harvest, and Knowledge Log.");
        }
    }

    private int seedCustomersIfEmpty() {
        if (!customerService.getAll().isEmpty()) {
            return 0;
        }
        customerService.add(new Customer(
                "Kitsap Blooms", "Sam Rivera", "sam@kitsapblooms.example",
                "360-555-0100", "FLORIST", "Weekly wholesale — Port Orchard"));
        customerService.add(new Customer(
                "Saturday Market Stall", "Alex Chen", "",
                "360-555-0142", "MARKET", "Port Orchard Farmers Market"));
        customerService.add(new Customer(
                "Nootka & Co. Events", "Jordan Lee", "events@nootka.example",
                "360-555-0199", "WHOLESALE", "Weddings / farm dinners"));
        return 3;
    }

    private int seedHarvestsIfEmpty(LocalDate today) {
        if (!harvestService.getAll().isEmpty()) {
            return 0;
        }
        harvestService.add(new HarvestEntry(
                today.minusDays(2), "Nootka Rose", 80, "stems", "Bed A", "demo morning cut"));
        harvestService.add(new HarvestEntry(
                today.minusDays(1), "Nootka Rose", 60, "stems", "Bed A", "demo"));
        harvestService.add(new HarvestEntry(
                today.minusDays(1), "Dahlia mix", 25, "bunches", "Bed C", "demo"));
        harvestService.add(new HarvestEntry(
                today, "Damask Rose", 40, "stems", "Tunnel 1", "demo today's cut"));
        harvestService.add(new HarvestEntry(
                today, "Nootka Rose", 50, "stems", "Bed A", "demo today's cut"));
        return 5;
    }

    private int seedOrdersIfEmpty(LocalDate today) {
        List<CustomerOrder> existing = orderService.findBetween(today.minusDays(6), today);
        if (!existing.isEmpty()) {
            return 0;
        }
        List<Customer> customers = customerService.getAll();
        if (customers.isEmpty()) {
            return 0;
        }
        Customer florist = customers.stream()
                .filter(c -> "FLORIST".equalsIgnoreCase(c.getCustomerType()))
                .findFirst().orElse(customers.get(0));
        Customer market = customers.stream()
                .filter(c -> "MARKET".equalsIgnoreCase(c.getCustomerType()))
                .findFirst().orElse(customers.get(Math.min(1, customers.size() - 1)));
        Customer wholesale = customers.stream()
                .filter(c -> "WHOLESALE".equalsIgnoreCase(c.getCustomerType()))
                .findFirst().orElse(customers.get(0));

        orderService.create(
                florist.getId(),
                today,
                "CONFIRMED",
                "demo — AM pickup",
                "PICKUP",
                List.of(
                        new OrderLine("Nootka Rose", 40, "stems", 2.50),
                        new OrderLine("Damask Rose", 12, "stems", 3.00)
                ));
        orderService.create(
                market.getId(),
                today,
                "CONFIRMED",
                "demo — Saturday stall",
                "MARKET",
                List.of(
                        new OrderLine("Nootka Rose", 30, "stems", 2.50),
                        new OrderLine("Dahlia mix", 8, "bunches", 12.00)
                ));
        orderService.create(
                wholesale.getId(),
                today,
                "DRAFT",
                "demo — hold for confirm",
                "DELIVERY",
                List.of(new OrderLine("Nootka Rose", 20, "stems", 2.20)));
        return 3;
    }

    private int seedKnowledgeIfEmpty(LocalDate today) {
        if (!knowledgeService.search(KnowledgeService.SearchRequest.all()).isEmpty()) {
            return 0;
        }
        YearMonth thisAugust = YearMonth.of(today.getYear(), Month.AUGUST);
        int lastAugustYear = thisAugust.atEndOfMonth().isBefore(today)
                ? today.getYear() : today.getYear() - 1;
        LocalDate lastAugust = LocalDate.of(lastAugustYear, Month.AUGUST, 18);

        knowledgeService.create(new KnowledgeEntry(
                lastAugust,
                "Powdery mildew slowed after earlier watering",
                KnowledgeKind.PROBLEM,
                "Nootka Rose", "", "Bed A", "Budding",
                "White patches returned after warm nights and late overhead watering.",
                "Removed affected leaves, increased spacing, and moved watering before 9 AM.",
                "Field crew",
                "No new patches appeared during the following seven-day check.",
                lastAugust.plusDays(14), KnowledgeConfidence.PROMISING,
                List.of("mildew", "irrigation", "timing")));
        knowledgeService.create(new KnowledgeEntry(
                today.minusDays(12),
                "Cooler harvest timing improved vase life",
                KnowledgeKind.PRACTICE,
                "Dahlia mix", "", "Bed C", "Harvest",
                "Afternoon cuts softened before market packing.",
                "Moved the main cut to early morning and placed stems directly into clean water.",
                "Harvest lead",
                "The trial bunch stayed firm through market day and the next morning.",
                today.plusDays(2), KnowledgeConfidence.EARLY_SIGNAL,
                List.of("harvest", "timing", "quality")));
        knowledgeService.create(new KnowledgeEntry(
                today.minusDays(3),
                "Saturday customers asked for mixed rose bunches",
                KnowledgeKind.OPPORTUNITY,
                "Rose", "Mixed", "Market", "Market feedback",
                "Several customers wanted one ready-priced bunch with three rose colors.",
                "Reserved enough stems for a six-bunch trial at the next market.",
                "Market lead", "Trial is scheduled; sales result is not known yet.",
                today, KnowledgeConfidence.NEEDS_REVIEW,
                List.of("market", "customer-request", "trial")));
        return 3;
    }

    private int seedSeasonPlanIfEmpty(LocalDate today) {
        int year = today.getYear();
        if (!seasonPlanService.listForSeason(year).isEmpty()) {
            return 0;
        }
        seasonPlanService.create(plan(year, "Nootka Rose", "Schoener's Nutkana", "Bed A",
                Month.FEBRUARY, 12, Month.APRIL, 1, Month.JUNE, 15,
                Month.SEPTEMBER, 20, 80, 640, SeasonPlanStatus.HARVESTING));
        seasonPlanService.create(plan(year, "Dahlia", "Cafe au Lait", "Bed C",
                Month.MARCH, 5, Month.MAY, 12, Month.JULY, 20,
                Month.OCTOBER, 10, 60, 480, SeasonPlanStatus.PLANTED));
        seasonPlanService.create(plan(year, "Sweet Pea", "Spencer Mix", "Tunnel 2",
                Month.JANUARY, 20, Month.MARCH, 1, Month.MAY, 10,
                Month.JULY, 15, 45, 360, SeasonPlanStatus.COMPLETE));
        seasonPlanService.create(plan(year, "Sunflower", "ProCut Plum", "Field C",
                Month.MAY, 18, Month.JUNE, 2, Month.JULY, 28,
                Month.SEPTEMBER, 5, 100, 900, SeasonPlanStatus.SOWN));
        return 4;
    }

    private static SeasonPlanEntry plan(int year, String crop, String variety, String bed,
                                        Month sowMonth, int sowDay,
                                        Month transplantMonth, int transplantDay,
                                        Month harvestMonth, int harvestDay,
                                        Month harvestEndMonth, int harvestEndDay,
                                        double bedFeet, int stems, SeasonPlanStatus status) {
        return new SeasonPlanEntry(
                year, crop, variety, bed, LocalDate.of(year, sowMonth, sowDay),
                LocalDate.of(year, transplantMonth, transplantDay),
                LocalDate.of(year, harvestMonth, harvestDay),
                LocalDate.of(year, harvestEndMonth, harvestEndDay),
                bedFeet, stems, status, "Demo seasonal planting block");
    }
}
