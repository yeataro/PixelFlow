/**
 * 
 * PixelFlow | Copyright (C) 2017 Thomas Diewald (www.thomasdiewald.com)
 * 
 * src - www.github.com/diwi/PixelFlow
 * 
 * A Processing/Java library for high performance GPU-Computing.
 * MIT License: https://opensource.org/licenses/MIT
 * 
 */



package com.thomasdiewald.pixelflow.java.imageprocessing.filter;

import com.jogamp.opengl.GLES3;
import com.thomasdiewald.pixelflow.java.DwPixelFlow;
import com.thomasdiewald.pixelflow.java.dwgl.DwGLSLProgram;
import com.thomasdiewald.pixelflow.java.dwgl.DwGLTexture;
import com.thomasdiewald.pixelflow.java.imageprocessing.filter.DwFilter;
import com.thomasdiewald.pixelflow.java.utils.DwUtils;



/**
 * 
 * 
 * Parallel reduction Min/Max filter.
 * 
 * Step size can be 2, 3 or 4, where 4 turns out to be a little bit faster.
 * 
 * @author Thomas
 *
 */
public class MinMax {
  

  protected int STEP_SIZE = 4; // 2,3,4
  
  public DwPixelFlow context;
  
  public int layers = 0;
  public DwGLTexture[] tex = new DwGLTexture[layers];
  
  public DwGLSLProgram shader_min;
  public DwGLSLProgram shader_max;
  
  public MinMax(DwPixelFlow context){
    this.context = context;
    
    this.shader_min = context.createShader((Object)(this+"_MIN"), DwPixelFlow.SHADER_DIR+"Filter/min_max.frag");
    this.shader_max = context.createShader((Object)(this+"_MAX"), DwPixelFlow.SHADER_DIR+"Filter/min_max.frag");
    
    shader_min.frag.setDefine("MODE_MIN", 1);
    shader_max.frag.setDefine("MODE_MAX", 1);
    
    shader_min.frag.setDefine("STEP_SIZE", STEP_SIZE);
    shader_max.frag.setDefine("STEP_SIZE", STEP_SIZE);
  }
  
  public void release(){
    for(int i = 0; i < tex.length; i++){
      if(tex[i] != null) tex[i].release();
    }
  }
  

  public void resize(DwGLTexture src){

    int w = src.w;
    int h = src.h;
    
    // 1) compute number of blur layers
    layers = Math.max(DwUtils.logNceil(w, STEP_SIZE), DwUtils.logNceil(h, STEP_SIZE)) + 1;
 
    // 2) init/release textures if needed
    if(tex.length < layers){
      release();
      tex = new DwGLTexture[layers];
      for(int i = 0; i < layers; i++){
        tex[i] = new DwGLTexture();
      }
    }

    // 3) allocate textures
    for(int i = 0; i < layers; i++){
      
      if(i == layers-1){
        w = 2;
        h = 1;
      }
      
      tex[i].resize(context, src, w, h);
      tex[i].setParam_WRAP_S_T(GLES3.GL_CLAMP_TO_EDGE);
//      System.out.println(i+", "+w+", "+h);
      w = (int) Math.ceil(w / (float) STEP_SIZE);
      h = (int) Math.ceil(h / (float) STEP_SIZE);
    }
  }
  
  public void apply(DwGLTexture tex_src){
    apply(tex_src, true, true);
  }

  public void apply(DwGLTexture tex_src, boolean MIN, boolean MAX){
    
    if(!MIN && !MAX){
      return;
    }
    
    resize(tex_src);
    
    DwFilter.get(context).copy.apply(tex_src, tex[0]);
    
    context.begin();
    
    if(MIN){
      for(int i = 1; i < layers; i++){
        
        DwGLTexture dst = tex[i];
        DwGLTexture src = tex[i-1];

        context.beginDraw(dst);
        shader_min.begin();
        shader_min.uniform2f("wh_rcp", 1f/src.w, 1f/src.h);
        shader_min.uniformTexture("tex", src);
        if(i == layers-1){
          shader_min.uniform2i("off", 1, 0);
          shader_min.drawFullScreenQuad(0,0,1,1); // pixel[0,0] == min
        } else {
          shader_min.uniform2i("off", 0, 0);
          shader_min.drawFullScreenQuad();
        }
        shader_min.end();
        context.endDraw();
      }
    }
    
    if(MAX){
      for(int i = 1; i < layers; i++){
        
        DwGLTexture dst = tex[i];
        DwGLTexture src = tex[i-1];

        context.beginDraw(dst);
        shader_max.begin();
        shader_max.uniform2f("wh_rcp", 1f/src.w, 1f/src.h);
        shader_max.uniformTexture("tex", src);
        if(i == layers-1){
          shader_max.uniform2i("off", 1, 0);
          shader_max.drawFullScreenQuad(1,0,1,1); // pixel[1,0] == max
        } else {
          shader_max.uniform2i("off", 0, 0);
          shader_max.drawFullScreenQuad();
        }
        shader_max.end();
        context.endDraw();
      }
    }
    
    context.end("MinMax.apply");
  }
  
  
  /**
   *  
   * @return  A two-pixel texture, where pixel(0,0) has the min-value and pixel(1,0) the max-value.
   * 
   */
  public DwGLTexture getVal(){
    return tex[layers-1];
  }
  
  
 
}
