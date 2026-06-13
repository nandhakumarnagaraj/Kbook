package com.khanabook.lite.pos.util

import android.content.Context
import com.facebook.flipper.android.AndroidFlipperClient
import com.facebook.flipper.android.utils.FlipperUtils
import com.facebook.flipper.plugins.inspector.DescriptorMapping
import com.facebook.flipper.plugins.inspector.InspectorFlipperPlugin
import com.facebook.flipper.plugins.network.NetworkFlipperPlugin
import com.facebook.soloader.SoLoader

object FlashlightInitializer {
    
    private var networkPlugin: NetworkFlipperPlugin? = null
    
    fun initialize(context: Context) {
        if (!FlipperUtils.shouldEnableFlipper(context)) {
            return
        }
        
        try {
            SoLoader.init(context, false)
            
            val client = AndroidFlipperClient.getInstance(context)
            
            networkPlugin = NetworkFlipperPlugin()
            client.addPlugin(networkPlugin)
            
            client.addPlugin(InspectorFlipperPlugin(context, DescriptorMapping.withDefaults()))
            
            client.start()
        } catch (e: Exception) {
            // Silently fail - performance monitoring is optional
        }
    }
    
    fun getNetworkPlugin(): NetworkFlipperPlugin? = networkPlugin
}
