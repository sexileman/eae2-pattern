package io.github.sexileman.eae2pattern.client;

import io.github.sexileman.eae2pattern.pattern.GenerationReport;

/** Lets whatever screen is open react to a finished server run without the protocol knowing about screens. */
public interface GenerationListener {

    void onGenerationFinished(GenerationReport report);
}
