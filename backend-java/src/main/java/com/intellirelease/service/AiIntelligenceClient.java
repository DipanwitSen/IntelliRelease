package com.intellirelease.service;

import com.intellirelease.model.ReleaseNarrative;
import com.intellirelease.model.ReleaseSummary;

public interface AiIntelligenceClient {
    ReleaseNarrative explain(ReleaseSummary summary);
}