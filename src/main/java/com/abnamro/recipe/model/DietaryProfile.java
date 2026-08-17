package com.abnamro.recipe.model;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

/**
 * Value object describing the dietary attributes of a {@link Recipe}, derived by
 * the server from the recipe's ingredients. Keyed by {@link DietaryFlag} rather than
 * by fixed boolean fields, so derivation, search, and this profile can never fall
 * out of sync and a new flag is picked up everywhere by adding a single enum
 * constant.
 *
 * <p>Persisted as a JSON document in the {@code dietary_profile_attributes} column
 * (see the converters in the config package). The custom (de)serializers below keep
 * the flat, one-key-per-flag wire format, e.g.
 * {@code {"vegetarian": true, "vegan": true, "meat": false, "gluten": false,
 * "wheat": false, "nut": false}} — driven by {@link DietaryFlag#values()} so the
 * shape tracks the enum automatically.
 */
@JsonSerialize(using = DietaryProfile.Serializer.class)
@JsonDeserialize(using = DietaryProfile.Deserializer.class)
public final class DietaryProfile {

    private final EnumSet<DietaryFlag> trueFlags;

    private DietaryProfile(EnumSet<DietaryFlag> trueFlags) {
        this.trueFlags = trueFlags;
    }

    /**
     * Derives the profile from the set of {@link IngredientType}s a recipe uses:
     * every flag whose ingredient-type rule holds is set. No flag can be skipped.
     */
    public static DietaryProfile from(Set<IngredientType> presentTypes) {
        EnumSet<DietaryFlag> on = EnumSet.noneOf(DietaryFlag.class);
        for (DietaryFlag flag : DietaryFlag.values()) {
            if (flag.evaluate(presentTypes)) {
                on.add(flag);
            }
        }
        return new DietaryProfile(on);
    }

    /** {@code true} when the given flag holds for this recipe. */
    public boolean is(DietaryFlag flag) {
        return trueFlags.contains(flag);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DietaryProfile other && trueFlags.equals(other.trueFlags);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(trueFlags);
    }

    @Override
    public String toString() {
        return "DietaryProfile" + trueFlags;
    }

    /** Writes the profile as a flat object of {@code flag.token() -> boolean}, one key per flag. */
    public static final class Serializer extends ValueSerializer<DietaryProfile> {
        @Override
        public void serialize(DietaryProfile value, JsonGenerator gen, SerializationContext ctxt) {
            gen.writeStartObject();
            for (DietaryFlag flag : DietaryFlag.values()) {
                gen.writeName(flag.token());
                gen.writeBoolean(value.is(flag));
            }
            gen.writeEndObject();
        }
    }

    /** Reads the flat {@code flag.token() -> boolean} object back into a profile. */
    public static final class Deserializer extends ValueDeserializer<DietaryProfile> {
        @Override
        public DietaryProfile deserialize(JsonParser p, DeserializationContext ctxt) {
            JsonNode node = ctxt.readTree(p);
            EnumSet<DietaryFlag> on = EnumSet.noneOf(DietaryFlag.class);
            for (DietaryFlag flag : DietaryFlag.values()) {
                if (node.path(flag.token()).booleanValue(false)) {
                    on.add(flag);
                }
            }
            return new DietaryProfile(on);
        }
    }
}
