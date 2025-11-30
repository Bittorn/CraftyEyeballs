package net.bittorn.craftyeyeballs.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.bittorn.craftyeyeballs.resolver.CraftyEyeballsResolver;
import net.bittorn.craftyeyeballs.resolver.ResolvedServer;
import net.bittorn.craftyeyeballs.util.HappyEyeballRunnable;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.Address;
import net.minecraft.client.network.AllowedAddressResolver;
import net.minecraft.client.network.MultiplayerServerListPinger;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Optional;

@Mixin(MultiplayerServerListPinger.class)
public class MultiplayerServerListPingerMixin {

    @Redirect(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/AllowedAddressResolver;resolve(Lnet/minecraft/client/network/ServerAddress;)Ljava/util/Optional;"), method = "add(Lnet/minecraft/client/network/ServerInfo;Ljava/lang/Runnable;Ljava/lang/Runnable;)V", allow = 1)
    public Optional<Address> add(AllowedAddressResolver instance, ServerAddress address) {
        // prevent unnecessary lookup
        return Optional.empty();
    }

    @Inject(method = "add(Lnet/minecraft/client/network/ServerInfo;Ljava/lang/Runnable;Ljava/lang/Runnable;)V", at = @At(value = "INVOKE", target = "Ljava/util/Optional;isEmpty()Z", shift = At.Shift.BEFORE), cancellable = true)
    public void injected(ServerInfo entry, Runnable saver, Runnable pingCallback, CallbackInfo ci, @Local ServerAddress serverAddress) throws UnknownHostException {
        // we hijack the runnable to store ip addresses
        if (!(saver instanceof HappyEyeballRunnable)) {
            ci.cancel();

            ResolvedServer resolved = CraftyEyeballsResolver.resolve(serverAddress);
            MultiplayerServerListPinger that = (MultiplayerServerListPinger) (Object) this;

            if (resolved.address6() == null && resolved.address4() == null) {
                // just give up at this point
                that.showError(ConnectScreen.UNKNOWN_HOST_TEXT, entry);
                return;
            }

            // Pings aren't performed parallel, so if a server has functioning V4 but a timing out V6, it'll take until
            // the v6 times out before the v4 is pinged. That is a very weird edge case tho, so I'm completely fine with that.

            try {
                if (resolved.address6() != null) {
                    that.add(entry, new HappyEyeballRunnable(saver, new InetSocketAddress(resolved.address6(), serverAddress.getPort())), pingCallback);
                    return;
                }
            } catch (Exception e) {
                if (resolved.address4() == null) throw e; // nothing else to try
            }

            if (resolved.address4() != null) {
                that.add(entry, new HappyEyeballRunnable(saver, new InetSocketAddress(resolved.address4(), serverAddress.getPort())), pingCallback);
                return;
            }

            // todo show like a cool v6 enabled icon, kinda like the forge checkmark meets SixIndicator

            return;
        }

//        ref.set(Optional.ofNullable(her.address()));
    }

}
