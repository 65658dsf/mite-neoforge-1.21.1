package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.combat.SourcePlayerCombat;
import io.github.ningmeng123.mite.entity.MobCorrosion;
import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.R196Cube;
import io.github.ningmeng123.mite.registry.MiteMobs;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real immune melee and player-contact corrosion feedback. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class CubeCorrosionSoundGameTests {
    @GameTest(template="empty")
    public static void immuneWhiteAndBlackCubesStillHissWhenTheyCorrodeTheAttackingSword(GameTestHelper h) {
        var player=h.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(h.absolutePos(new BlockPos(2,3,2)).getCenter());
        var sounds=new ArrayList<PlayLevelSoundEvent.AtPosition>();
        Consumer<PlayLevelSoundEvent.AtPosition> listener=e->{if(e.getLevel()==h.getLevel()&&e.getSound()!=null&&e.getSound().value()==SoundEvents.FIRE_EXTINGUISH)sounds.add(e);};
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            for(var kind:List.of(MobKind.OOZE,MobKind.PUDDING)) {
                R196Cube cube=(R196Cube)MiteMobs.type(kind).create(h.getLevel());
                cube.moveTo(player.position());cube.setSize(1,true);
                var sword=new ItemStack(Items.IRON_SWORD);player.setItemSlot(EquipmentSlot.MAINHAND,sword);
                var source=player.damageSources().playerAttack(player);
                int before=sounds.size();
                SourcePlayerCombat.primaryHit(player,cube,source,3,()->cube.hurt(source,3));
                h.assertTrue(sword.getDamageValue()>0,"Even an immune cube's actual melee callback corrodes a susceptible sword: "+kind);
                h.assertValueEqual(sounds.size(),before+1,"One real corrosion sound is broadcast for the attacking player's sword: "+kind);
                var sound=sounds.getLast();
                h.assertTrue(sound.getOriginalVolume()==.7F&&sound.getOriginalPitch()>=1.2F&&sound.getOriginalPitch()<=2F,
                        "The original steam_with_hiss volume and pitch bounds are retained");
            }
            int before=sounds.size();
            var lapis=new ItemStack(Items.LAPIS_LAZULI);
            h.assertFalse(MobCorrosion.equipment(lapis,true,4,player,EquipmentSlot.MAINHAND),"Acid-immune lapis is not corroded");
            h.assertValueEqual(sounds.size(),before,"No corrosion means no misleading hiss");
        } finally {NeoForge.EVENT_BUS.unregister(listener);}
        h.succeed();
    }

    @GameTest(template="empty")
    public static void corrosivePlayerContactHissesOnceEvenWhenAllFourArmorPiecesCorrode(GameTestHelper h) {
        var player=h.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(h.absolutePos(new BlockPos(2,3,2)).getCenter());
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);player.setHealth(100);
        player.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET));
        player.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.IRON_CHESTPLATE));
        player.setItemSlot(EquipmentSlot.LEGS,new ItemStack(Items.IRON_LEGGINGS));
        player.setItemSlot(EquipmentSlot.FEET,new ItemStack(Items.IRON_BOOTS));
        R196Cube cube=(R196Cube)MiteMobs.type(MobKind.PUDDING).create(h.getLevel());
        cube.setSize(4,true);cube.moveTo(player.position());
        int[] sounds={0};
        Consumer<PlayLevelSoundEvent.AtPosition> listener=e->{if(e.getLevel()==h.getLevel()&&e.getSound()!=null&&e.getSound().value()==SoundEvents.FIRE_EXTINGUISH)sounds[0]++;};
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            cube.playerTouch(player);
            h.assertValueEqual(sounds[0],1,"The cube's real attack emits one shared inventory-corrosion hiss");
            for(var slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET))
                h.assertTrue(player.getItemBySlot(slot).getDamageValue()>0,"The size-four contact reaches each armor piece: "+slot);
            cube.playerTouch(player);
            h.assertValueEqual(sounds[0],1,"A contact rejected by damage invulnerability cannot replay corrosion feedback");
        } finally {NeoForge.EVENT_BUS.unregister(listener);}
        h.succeed();
    }
}
