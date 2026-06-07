package com.ziqi.codesim.next;

import java.util.List;

public interface CandidateSignalProvider {
    List<CandidateSignal> findSignals(EvidencePackage evidencePackage);
}
