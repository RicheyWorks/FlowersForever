package com.flowerfarm.automation.farm;

import com.flowerfarm.automation.HarnessExecution;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

/** Keeps a complete farm command and its import receipts in the same transaction. */
@Service
public class FarmDataCommandTransactions {

    @Transactional
    public HarnessExecution execute(Supplier<HarnessExecution> operation) {
        return operation.get();
    }
}
