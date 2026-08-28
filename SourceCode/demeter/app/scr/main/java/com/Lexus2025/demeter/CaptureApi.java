// =============================================================================
// Demeter Reshader
// Copyright (C) 2025  LexusYTG
//
// This file is part of Demeter Reshader.
//
// Demeter Reshader is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
// ...
// =============================================================================

package com.Lexus2025.demeter;

import android.graphics.Bitmap;

public class CaptureApi {

    private TargetRenderer mRenderer;
    private ModuleManager mModuleManager;

    public CaptureApi(TargetRenderer renderer) {
        mRenderer = renderer;
    }

    public void setRenderer(TargetRenderer renderer) {
        mRenderer = renderer;
    }

    public void setModuleManager(ModuleManager moduleManager) {
        mModuleManager = moduleManager;
        if (mRenderer != null) {
            mRenderer.setModuleManager(moduleManager);
        }
    }

    public void setTestMode(boolean testMode) {
        if (mRenderer != null) {
            mRenderer.setTestMode(testMode);
        }
    }

    public void sendFrame(Bitmap frame) {
        if (mRenderer != null) {
            mRenderer.receiveFrame(frame);
        }
    }
  }
