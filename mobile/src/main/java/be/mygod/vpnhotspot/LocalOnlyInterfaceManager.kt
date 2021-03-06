package be.mygod.vpnhotspot

import android.widget.Toast
import be.mygod.vpnhotspot.App.Companion.app
import be.mygod.vpnhotspot.net.Routing
import be.mygod.vpnhotspot.net.UpstreamMonitor
import com.crashlytics.android.Crashlytics
import java.net.InetAddress
import java.net.InterfaceAddress
import java.net.SocketException

class LocalOnlyInterfaceManager(val downstream: String) : UpstreamMonitor.Callback {
    private var routing: Routing? = null
    private var dns = emptyList<InetAddress>()

    init {
        app.cleanRoutings[this] = this::clean
        UpstreamMonitor.registerCallback(this) { initRouting() }
    }

    override fun onAvailable(ifname: String, dns: List<InetAddress>) {
        val routing = routing
        initRouting(ifname, if (routing == null) null else {
            routing.stop()
            routing.hostAddress
        }, dns)
    }
    override fun onLost() {
        val routing = routing ?: return
        if (!routing.stop()) app.toast(R.string.noisy_su_failure)
        initRouting(null, routing.hostAddress, emptyList())
    }

    private fun clean() {
        val routing = routing ?: return
        routing.started = false
        initRouting(routing.upstream, routing.hostAddress, dns)
    }

    private fun initRouting(upstream: String? = null, owner: InterfaceAddress? = null,
                            dns: List<InetAddress> = this.dns) {
        try {
            val routing = Routing(upstream, downstream, owner)
            this.routing = routing
            this.dns = dns
            val strict = app.pref.getBoolean("service.repeater.strict", false)
            if (strict && upstream == null) return  // in this case, nothing to be done
            routing.ipForward()                     // local only interfaces need not enable ip_forward
                    .rule().forward(strict)
            if (app.masquerade) routing.masquerade(strict)
            if (!routing.dnsRedirect(dns).start()) app.toast(R.string.noisy_su_failure)
        } catch (e: SocketException) {
            Toast.makeText(app, e.message, Toast.LENGTH_SHORT).show()
            Crashlytics.logException(e)
            routing = null
        }
    }

    fun stop() {
        UpstreamMonitor.unregisterCallback(this)
        app.cleanRoutings -= this
        if (routing?.stop() == false) app.toast(R.string.noisy_su_failure)
    }
}
