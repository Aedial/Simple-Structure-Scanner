package com.simplestructurescanner.client.command;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.simplestructurescanner.structure.StructureProvider;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.client.IClientCommand;

import com.simplestructurescanner.searching.StructureSearchManager;
import com.simplestructurescanner.structure.StructureProviderRegistry;
import com.simplestructurescanner.structure.StructureSearchOverrides;


/**
 * Client-side command for removing persistent hidden and search blacklist entries.
 */
public class CommandStructureSearchBlacklist extends CommandBase implements IClientCommand {

    private static final List<String> BLACKLIST_TYPES = Arrays.asList("hidden", "search");
    private static final List<String> ACTIONS = Collections.singletonList("remove");
    private static final List<String> ENTRY_TYPES = Arrays.asList("structure", "dimension", "structure_dimension");

    private static final class ParsedTarget {
        private final StructureSearchOverrides.EntryType entryType;
        private final ResourceLocation structureId;
        private final Integer dimensionId;

        private ParsedTarget(StructureSearchOverrides.EntryType entryType, ResourceLocation structureId,
                Integer dimensionId) {
            this.entryType = entryType;
            this.structureId = structureId;
            this.dimensionId = dimensionId;
        }
    }

    @Override
    public String getName() {
        return "sssblacklist";
    }

    @Override
    public List<String> getAliases() {
        return Collections.singletonList("structurescannerblacklist");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/sssblacklist <hidden|search> remove <provider> <structure|dimension|structure_dimension> <value...>";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 5) throw new WrongUsageException(getUsage(sender));

        StructureSearchOverrides.BlacklistType blacklistType = parseBlacklistType(sender, args[0]);
        if (!"remove".equalsIgnoreCase(args[1])) throw new WrongUsageException(getUsage(sender));

        String providerId = args[2].toLowerCase(Locale.ROOT);
        ParsedTarget target = parseTarget(sender, providerId, args, 3);

        removeFileEntry(sender, blacklistType, providerId, target);
    }

    @Override
    public boolean allowUsageWithoutPrefix(ICommandSender sender, String message) {
        return false;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
            net.minecraft.util.math.BlockPos targetPos) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, BLACKLIST_TYPES);
        if (args.length == 2) return getListOfStringsMatchingLastWord(args, ACTIONS);
        if (args.length == 3) {
            List<String> providerIds = StructureProviderRegistry.getProviders().stream()
                .map(StructureProvider::getProviderId)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
            return getListOfStringsMatchingLastWord(args, providerIds);
        }
        if (args.length == 4) return getListOfStringsMatchingLastWord(args, ENTRY_TYPES);

        return Collections.emptyList();
    }

    private static void reloadProvider(String providerId) {
        StructureProviderRegistry.reloadProvider(providerId);
        StructureSearchManager.clearCaches();
    }

    private void removeFileEntry(ICommandSender sender, StructureSearchOverrides.BlacklistType blacklistType,
            String providerId, ParsedTarget target) {
        boolean removed = StructureSearchOverrides.removeEntry(blacklistType, providerId,
            target.entryType, target.structureId, target.dimensionId);

        if (removed) {
            reloadProvider(providerId);
            sender.sendMessage(createRemoveMessage(true, blacklistType, providerId, target));
            return;
        }

        sender.sendMessage(createRemoveMessage(false, blacklistType, providerId, target));
    }

    private ITextComponent createRemoveMessage(boolean removed, StructureSearchOverrides.BlacklistType blacklistType,
            String providerId, ParsedTarget target) {
        switch (target.entryType) {
            case STRUCTURE:
                return new TextComponentTranslation(
                    removed ? "commands.structurescanner.blacklist.removedStructure"
                        : "commands.structurescanner.blacklist.notFoundStructure",
                    providerId, target.structureId, describeBlacklistType(blacklistType));
            case DIMENSION:
                return new TextComponentTranslation(
                    removed ? "commands.structurescanner.blacklist.removedDimension"
                        : "commands.structurescanner.blacklist.notFoundDimension",
                    providerId, target.dimensionId, describeBlacklistType(blacklistType));
            case STRUCTURE_DIMENSION:
                return new TextComponentTranslation(
                    removed ? "commands.structurescanner.blacklist.removedStructureDimension"
                        : "commands.structurescanner.blacklist.notFoundStructureDimension",
                    providerId, target.structureId, target.dimensionId, describeBlacklistType(blacklistType));
            default:
                return new TextComponentString(getUsage(null));
        }
    }

    private StructureSearchOverrides.BlacklistType parseBlacklistType(ICommandSender sender, String token)
            throws CommandException {
        switch (token.toLowerCase(Locale.ROOT)) {
            case "hidden":
                return StructureSearchOverrides.BlacklistType.HIDDEN;
            case "search":
            case "searchable":
                return StructureSearchOverrides.BlacklistType.SEARCH;
            default:
                throw new WrongUsageException(getUsage(sender));
        }
    }

    private ParsedTarget parseTarget(ICommandSender sender, String providerId, String[] args,
            int typeIndex) throws CommandException {
        String type = args[typeIndex].toLowerCase(Locale.ROOT);

        switch (type) {
            case "structure":
                if (args.length != typeIndex + 2) throw new WrongUsageException(getUsage(sender));

                return new ParsedTarget(StructureSearchOverrides.EntryType.STRUCTURE,
                    parseStructureId(providerId, args[typeIndex + 1]), null);
            case "dimension":
                if (args.length != typeIndex + 2) throw new WrongUsageException(getUsage(sender));

                return new ParsedTarget(StructureSearchOverrides.EntryType.DIMENSION,
                    null, parseInt(args[typeIndex + 1]));
            case "structure_dimension":
                if (args.length != typeIndex + 3) throw new WrongUsageException(getUsage(sender));

                return new ParsedTarget(StructureSearchOverrides.EntryType.STRUCTURE_DIMENSION,
                    parseStructureId(providerId, args[typeIndex + 1]), parseInt(args[typeIndex + 2]));
            default:
                throw new WrongUsageException(getUsage(sender));
        }
    }

    private static ITextComponent describeBlacklistType(StructureSearchOverrides.BlacklistType blacklistType) {
        switch (blacklistType) {
            case HIDDEN:
                return new TextComponentTranslation("commands.structurescanner.blacklist.type.hidden");
            case SEARCH:
                return new TextComponentTranslation("commands.structurescanner.blacklist.type.search");
            default:
                return new TextComponentString("");
        }
    }

    private static ResourceLocation parseStructureId(String providerId, String token) {
        return token.contains(":") ? new ResourceLocation(token) : new ResourceLocation(providerId, token);
    }
}