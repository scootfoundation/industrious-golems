package com.scottcaldwell.industriousgolems;

import com.scottcaldwell.industriousgolems.golem.WorkGolem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;

/** One golem entity type per profession: the hay golem farms, the mushroom golem fells trees, the terracotta golem herds. */
@EventBusSubscriber(modid = IndustriousGolems.MODID)
public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, IndustriousGolems.MODID);

    private static final Map<String, ModProfessions.Profession> PROFESSION_BY_ID = new LinkedHashMap<>();

    /** The hay golem. */
    public static final DeferredHolder<EntityType<?>, EntityType<WorkGolem>> FARMER_GOLEM =
            define(ModProfessions.FARMER, "hay_golem", 0.6F, 1.65F);
    /** The terracotta golem. */
    public static final DeferredHolder<EntityType<?>, EntityType<WorkGolem>> HERDER_GOLEM =
            define(ModProfessions.HERDER, "terracotta_golem", 0.8F, 1.8F);
    /** The mushroom golem. */
    public static final DeferredHolder<EntityType<?>, EntityType<WorkGolem>> LUMBERJACK_GOLEM =
            define(ModProfessions.LUMBERJACK, "mushroom_golem", 0.7F, 1.3F);

    private ModEntities() { }

    private static DeferredHolder<EntityType<?>, EntityType<WorkGolem>> define(
            ModProfessions.Profession profession, String id, float width, float height) {
        PROFESSION_BY_ID.put(id, profession);
        return ENTITIES.register(id, () -> EntityType.Builder.<WorkGolem>of(WorkGolem::new, MobCategory.CREATURE)
                .sized(width, height)
                .eyeHeight(height * 0.9F)
                .clientTrackingRange(10)
                .build(ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(IndustriousGolems.MODID, id))));
    }

    /** The profession carried out by golems of this entity type. */
    public static ModProfessions.Profession professionOf(EntityType<?> type) {
        return PROFESSION_BY_ID.get(EntityType.getKey(type).getPath());
    }

    /** The golem entity type that carries out this profession. */
    public static EntityType<WorkGolem> typeOf(ModProfessions.Profession profession) {
        for (var type : all()) {
            if (professionOf(type.get()) == profession) return type.get();
        }
        throw new IllegalArgumentException("No golem for profession " + profession.id());
    }

    public static Iterable<DeferredHolder<EntityType<?>, EntityType<WorkGolem>>> all() {
        return java.util.List.of(FARMER_GOLEM, LUMBERJACK_GOLEM, HERDER_GOLEM);
    }

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        for (var type : all()) event.put(type.get(), WorkGolem.createAttributes(professionOf(type.get()).speed()).build());
    }

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
    }
}
