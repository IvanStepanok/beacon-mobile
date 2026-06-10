package com.stepanok.undp.domain.model

/** One selectable answer in a modular form section. [value] is the wire value stored in the
 *  report's modular blob (the enum `.name.lowercase()` for the built-in options); [label] is
 *  the server's display text — used as-is for sections the app has no localized strings for. */
data class FormOption(val value: String, val label: String)

enum class FormSectionType { SINGLE, MULTI }

/** One modular capture-form question, as served by GET /form-schema (the built-in Appendix-1
 *  defaults with the crisis's required/disabled overrides applied). */
data class FormSection(
    val key: String,
    val title: String,
    val type: FormSectionType,
    /** Required sections gate Continue on the modular step (per-crisis override; default false). */
    val required: Boolean = false,
    /** Section with an "Other → specify" free text, stored as "<key>Other" in the blob. */
    val allowOtherText: Boolean = false,
    val options: List<FormOption> = emptyList(),
)

/** Built-in fallback identical to the server's defaults (same keys, wire values and order) —
 *  used when the schema was never fetched and nothing is cached, so the capture form always
 *  works offline. The English labels are the verbatim Appendix-1 answer texts; the UI overlays
 *  its localized strings for these known keys/values at render time. */
fun defaultFormSections(): List<FormSection> = listOf(
    FormSection(
        key = "electricity",
        title = "Electricity infrastructure condition",
        type = FormSectionType.SINGLE,
        options = listOf(
            FormOption("none_observed", "No damage observed"),
            FormOption("minor", "Minor damage (service disruptions but quickly repairable)"),
            FormOption("moderate", "Moderate damage (partial outages requiring repairs)"),
            FormOption("severe", "Severe damage (major infrastructure damaged, prolonged outages)"),
            FormOption("destroyed", "Completely destroyed (no electricity infrastructure functioning)"),
            FormOption("unknown", "Unknown/cannot be assessed"),
        ),
    ),
    FormSection(
        key = "healthServices",
        title = "Health services functioning",
        type = FormSectionType.SINGLE,
        options = listOf(
            FormOption("fully_functional", "Fully functional"),
            FormOption("partially_functional", "Partially functional"),
            FormOption("largely_disrupted", "Largely disrupted"),
            FormOption("not_functioning", "Not functioning at all"),
            FormOption("unknown", "Unknown"),
        ),
    ),
    FormSection(
        key = "pressingNeeds",
        title = "Most pressing needs",
        type = FormSectionType.MULTI,
        allowOtherText = true, // "Other, please specify" → pressingNeedsOther in the blob
        options = listOf(
            FormOption("food_water", "Food assistance and safe drinking water"),
            FormOption("cash", "Cash or financial assistance"),
            FormOption("healthcare", "Access to healthcare and essential medicines"),
            FormOption("shelter", "Shelter, housing repair, or temporary accommodation"),
            FormOption("livelihoods", "Restoration of livelihoods or income sources"),
            FormOption("wash", "Water, sanitation, and hygiene (toilets, washing facilities)"),
            FormOption("basic_services", "Restoration of basic services and infrastructure (electricity, roads, schools)"),
            FormOption("protection", "Protection services and psychosocial support"),
            FormOption("local_support", "Support from local authorities and community organizations"),
            FormOption("other", "Other"),
        ),
    ),
)

/** The wire value of the "Other" option — selecting it reveals the free-text field
 *  on sections with [FormSection.allowOtherText]. */
const val FORM_OTHER_VALUE = "other"
