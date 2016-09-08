/*
 *  Copyright (c) 2014, Lukas Tenbrink.
 *  * http://lukas.axxim.net
 */

package ivorius.reccomplex.structures.generic.matchers;

import com.google.common.collect.Lists;
import ivorius.ivtoolkit.tools.IvGsonHelper;
import ivorius.reccomplex.json.RCGsonHelper;
import ivorius.reccomplex.utils.FunctionExpressionCache;
import ivorius.reccomplex.utils.algebra.RCBoolAlgebra;
import joptsimple.internal.Strings;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

/**
 * Created by lukas on 19.09.14.
 */
public class BiomeMatcher extends FunctionExpressionCache<Boolean, Biome, Set<Biome>> implements Predicate<Biome>
{
    public static final String BIOME_NAME_PREFIX = "name=";
    public static final String BIOME_TYPE_PREFIX = "type=";

    public BiomeMatcher(String expression)
    {
        super(RCBoolAlgebra.algebra(), true, TextFormatting.GREEN + "Any Biome", expression);

        addTypes(new BiomeVariableType(BIOME_NAME_PREFIX, ""), t -> t.alias("", ""));
        addTypes(new BiomeDictVariableType(BIOME_TYPE_PREFIX, ""), t -> t.alias("$", ""));

        testVariables();
    }

    public static String ofTypes(BiomeDictionary.Type... biomeTypes)
    {
        return BIOME_TYPE_PREFIX + Strings.join(Lists.transform(Arrays.asList(biomeTypes), input -> input != null ? IvGsonHelper.serializedName(input) : null), " & " + BIOME_TYPE_PREFIX);
    }

    public static Set<Biome> gatherAllBiomes()
    {
        Set<Biome> set = new HashSet<>();

        for (Biome biome : Biome.REGISTRY)
        {
            if (biome != null)
                set.add(biome);
        }

        for (BiomeDictionary.Type type : BiomeDictionary.Type.values())
        {
            try
            {
                Collections.addAll(set, BiomeDictionary.getBiomesForType(type));
            }
            catch (Exception ignored) // list f'd up by a biome mod
            {

            }
        }

        return set;
    }

    public Validity variableValidity()
    {
        return validity(gatherAllBiomes());
    }

    @Nonnull
    public String getDisplayString()
    {
        return getDisplayString(gatherAllBiomes());
    }

    @Override
    public boolean test(final Biome input)
    {
        return evaluate(input);
    }

    protected class BiomeVariableType extends VariableType<Boolean, Biome, Set<Biome>>
    {
        public BiomeVariableType(String prefix, String suffix)
        {
            super(prefix, suffix);
        }

        @Override
        public Boolean evaluate(String var, Biome biome)
        {
            return biome.getBiomeName().equals(var);
        }

        @Override
        public Validity validity(final String var, final Set<Biome> biomes)
        {
            return StreamSupport.stream(biomes.spliterator(), false).anyMatch(input -> input.getBiomeName().equals(var))
                    ? Validity.KNOWN : Validity.UNKNOWN;
        }
    }

    protected class BiomeDictVariableType extends VariableType<Boolean, Biome, Set<Biome>>
    {
        public BiomeDictVariableType(String prefix, String suffix)
        {
            super(prefix, suffix);
        }

        @Override
        public Boolean evaluate(String var, Biome biome)
        {
            BiomeDictionary.Type type = RCGsonHelper.enumForNameIgnoreCase(var, BiomeDictionary.Type.values());
            return type != null && BiomeDictionary.isBiomeOfType(biome, type);
        }

        @Override
        public Validity validity(String var, Set<Biome> biomes)
        {
            return RCGsonHelper.enumForNameIgnoreCase(var, BiomeDictionary.Type.values()) != null
                    ? Validity.KNOWN : Validity.UNKNOWN;
        }
    }
}
