package scorchat.harmonious.patches.discord.misc.changebackend

import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.patch.stringOption
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.ReferenceType
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21c
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction31c
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction21c
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction31c
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference

@Suppress("unused")
val changeBackendPatch = bytecodePatch(
    name = "Change Backend",
    description = "Allows changing the base URLs for API, Gateway, CDN, etc. to connect to custom instances."
) {
    compatibleWith("com.discord"("126.21"))

    val host by stringOption(key = "host", default = "https://discord.com", title = "Host URL", required = true)
    val hostApi by stringOption(key = "host_api", default = "https://discord.com/api/", title = "API URL", required = true)
    val hostCdn by stringOption(key = "host_cdn", default = "https://cdn.discordapp.com", title = "CDN URL", required = true)
    val hostInvite by stringOption(key = "host_invite", default = "https://discord.gg", title = "Invite URL", required = true)
    val hostGift by stringOption(key = "host_gift", default = "https://discord.gift", title = "Gift URL")
    val hostAlternate by stringOption(key = "host_alternate", default = "https://discordapp.com", title = "Alternate Host URL")
    val hostTemplate by stringOption(key = "host_template", default = "https://discord.new", title = "Guild Template URL")
    val hostMedia by stringOption(key = "host_media", default = "https://media.discordapp.net", title = "Media Proxy URL")
    val hostDev by stringOption(key = "host_dev", default = "https://discord.com/developers", title = "Developer Portal URL")

    execute {
        val replacements = mapOf(
            "https://discord.com/api/" to hostApi,
            "https://discord.com/developers" to hostDev,
            "https://discord.com" to host,
            "https://discordapp.com" to hostAlternate,
            "https://cdn.discordapp.com" to hostCdn,
            "https://media.discordapp.net" to hostMedia,
            "https://discord.gift" to hostGift,
            "https://discord.gg" to hostInvite,
            "https://discord.new" to hostTemplate
        )

        classes.forEach { classDef ->
            val needsPatching = classDef.methods.any { method ->
                method.implementation?.instructions?.any { instruction ->
                    if (instruction is ReferenceInstruction && instruction.referenceType == ReferenceType.STRING) {
                        val ref = instruction.reference as StringReference
                        replacements.keys.any { ref.string.startsWith(it) }
                    } else {
                        false
                    }
                } == true
            }

            if (needsPatching) {
                val mutableClass = proxy(classDef)

                mutableClass.mutableClass.methods.forEach { method ->
                    val implementation = method.implementation ?: return@forEach

                    @Suppress("UNCHECKED_CAST")
                    val instructions = implementation.instructions as? MutableList<Instruction>
                        ?: return@forEach

                    val iterator = instructions.listIterator()
                    while (iterator.hasNext()) {
                        val instruction = iterator.next()

                        if (instruction is ReferenceInstruction && instruction.referenceType == ReferenceType.STRING) {
                            val originalString = (instruction.reference as StringReference).string
                            val match = replacements.entries.find { originalString.startsWith(it.key) }

                            if (match != null) {
                                val newStringValue = match.value + originalString.substring(match.key.length)
                                val newReference = ImmutableStringReference(newStringValue)

                                val newInstruction = when (instruction.opcode) {
                                    Opcode.CONST_STRING -> {
                                        val instr21c = instruction as Instruction21c
                                        ImmutableInstruction21c(Opcode.CONST_STRING, instr21c.registerA, newReference)
                                    }
                                    Opcode.CONST_STRING_JUMBO -> {
                                        val instr31c = instruction as Instruction31c
                                        ImmutableInstruction31c(Opcode.CONST_STRING_JUMBO, instr31c.registerA, newReference)
                                    }
                                    else -> null
                                }

                                if (newInstruction != null) {
                                    iterator.set(newInstruction)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
