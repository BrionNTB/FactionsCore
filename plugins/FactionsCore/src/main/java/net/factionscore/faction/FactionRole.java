package net.factionscore.faction;

public enum FactionRole {
    RECRUIT,
    MEMBER,
    OFFICER,
    LEADER;

    public boolean canManageClaims() {
        return this == OFFICER || this == LEADER;
    }

    public boolean canInvite() {
        return this == OFFICER || this == LEADER;
    }

    public boolean canKick() {
        return this == OFFICER || this == LEADER;
    }

    public boolean canSetRelations() {
        return this == LEADER;
    }

    public boolean canDisband() {
        return this == LEADER;
    }
}
