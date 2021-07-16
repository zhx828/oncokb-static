package org.mskcc.cbio.oncokb.domain.enumeration;

/**
 * The LicenseType enumeration.
 */
public enum LicenseType {
    ACADEMIC("Research Use in an Academic Setting", "Research in Academic"),
    COMMERCIAL("Commercial Use", "Commercial Use"),
    RESEARCH_IN_COMMERCIAL("Research Use in a Commercial Setting", "Research in Commercial"),
    HOSPITAL("Hospital Use", "Hospital Use");
    String name;
    String shortName;

    LicenseType(String name, String shortName) {
        this.name = name;
        this.shortName = shortName;
    }

    public String getName() { return name; }

    public String getShortName() { return shortName; }
}
