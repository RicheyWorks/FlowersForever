package com.flowerfarm.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Provides a canonical trailing-slash URL for the installable worker web app. */
@Controller
public class WorkerPageController {

    @GetMapping("/worker")
    public String worker() {
        return "redirect:/worker/";
    }

    @GetMapping("/worker/")
    public String workerIndex() {
        return "forward:/worker/index.html";
    }
}
