# -*- coding: utf-8 -*-
"""
Code zur Emittanz Berechnung

"""


## imports
import sys
import numpy as np
import matplotlib.pyplot as plt
# import pickle
# import datetime as dt
# import pandas as pd
import scipy.constants as sciC
from scipy.optimize import curve_fit


# plt.rcParams.update({'font.size':20})
plt.rcParams.update({'font.size':24})


def calc_energy(Pc):
    # Pc = relativistic impuls in Mev
    # E  = particle Energy in MeV
    e = sciC.e
    c = sciC.speed_of_light
    m0 = sciC.m_e
    E0 = (m0 * c**2) / e / 1E6
    
    E = np.sqrt( Pc**2 - E0**2)
    return E

def calc_Pc(E):
    # Pc = relativistic impuls in Mev
    # E  = particle Energy in MeV
    e = sciC.e
    c = sciC.speed_of_light
    m0 = sciC.m_e
    E0 = (m0 * c**2) / e / 1E6
    
    Pc = np.sqrt( E**2 + E0**2)
    
    return Pc
    
def magnet_strenght(I , E=5):
    # mu0 = 1.2566E-6
    # N = 20
    # R = 0.0228
    # g = 2 * mu0 * N * I / R**2
    e = sciC.e
    Pc = calc_Pc(E) *1E6  # in eV/c
    Pc = Pc*e/sciC.speed_of_light
    
    m = -0.101392
    b = -0.002657
    g = m * I +b 
    
    k = e * g / Pc
    return k

def gamma_factor(E):
    # Energy in MeV
    e = sciC.e
    c = sciC.speed_of_light
    m0 = sciC.m_e
    E0 = (m0 * c**2) / e / 1E6
    print(E0)
    gamma = E/E0
    return gamma
    
def beta_vc(gamma):
    ga = gamma**2
    temp = (ga-1)/ga
    return np.sqrt(temp)

def momentum_test(E):
    e = sciC.e
    c = sciC.speed_of_light
    m0 = sciC.m_e
    E0 = (m0 * c**2) / e / 1E6
    gamma = gamma_factor(E)
    beta = beta_vc(gamma)
    
    return E0*c*beta*gamma


def emittance(x0,p0,xp0):
    
    #print(str(x0)+' * '+str(p0)+' - '+str(xp0)+'**2')
    emit=x0 * p0 - xp0**2
    if (emit<0):
        emit=0
    else:
        emit = np.sqrt( emit )   
    
    return emit

def full_function(current,s1,s2,s3):
    
    d = 1.4095
    E = 5.82
    k = magnet_strenght(current,E)
    l = 0.08
    k = np.sqrt(abs(k))

    C = np.cos(k*l)
    S = np.sin(k*l)
    
    # sigma = (C - d*k*S)**2 *s1**2 + 2* (C-d*k*S)*(S/k + d*C)*s3 + (S/k + d*C)**2 *s2
    A = C - k*d*S
    B = S/k + d*C
    
    sigma  = A**2 * s1 + 2 *A*B*s3 + B**2 *s2
    return sigma

def emit_error(x0, p0, xp0, d_x0, d_p0, d_xp0):
    e = emittance(x0, p0, xp0)
    if e==0:
        return 0
    
    dx = p0 / 2 / e
    
    dp = x0 / 2 / e
    
    dxp = xp0 / e
    
    emit_err = np.sqrt( (dx * d_x0)**2 + (dp* d_p0)**2 + (dxp * d_xp0 )**2)
    
    return emit_err


def emit2_error(x0, p0, xp0, d_x0, d_p0, d_xp0):
    
    emit_err = np.sqrt( (p0 * d_x0)**2 + (x0* d_p0)**2 + (2*xp0 * d_xp0 )**2)
    
    return emit_err


def norm_emit(emit,E):
    gamma = gamma_factor(E)
    beta = beta_vc(gamma)
    norm_e = emit*beta*gamma
    return norm_e

def norm_floet(emit,E):
    P = calc_Pc(E)
    c = sciC.speed_of_light
    # m0 = sciC.m_e
    m0 = 0.510998950 #MeV
    norm = emit * P / m0
    return norm

def f(x,a,b,c):
    return a*x**2+b*x+c

def f2(k,s1,s2,s3):
    d = 1.4095
    l = 0.08
    sig = (1 +d*k*l)**2 * s1 + 2*(1+d*k*l)*d*s3 + d**2 * s2
    return sig

def fit_emitance(current, mean_beam_width,std,plotting = True,Energy = 5.81, direct='x'): # single data set, quad current (A), beam size(mm)

    # current - quadrupole current in A, array
    # mean_beam_width - beam size mm
    # std
    # plotting = True,Energy = 5.81, direct='x'


    d = 1.4095
    E = Energy
    k = magnet_strenght(current,E)
    l = 0.08
    # x = k*l
    x = k
    std = 2*mean_beam_width * std
    mean_beam_width = mean_beam_width**2
    
    
    par,pcov = curve_fit(f, x, mean_beam_width, sigma=std, absolute_sigma=False, p0=[0.001,0.001,0.001])
    perr = np.sqrt(np.diag(pcov))
    
    a = par[0]
    da = perr[0]
    b = par[1]
    db = perr[1]
    c = par[2]
    dc = perr[2]
    dd = 0.001
    
    # print('fit-par: ',da/a,db/b,dc/c,dd/d)
    
    x0 = a/(d**2)  /(l**2)
    d_x0 = np.sqrt( (1/d**2 * da)**2 + (2*a /d**3 * dd )**2 )
    
    # x0p0 = b/(2*d**2) - a/(d**3)   
    x0p0 = b/(2*d**2)/l - a/(d**3)/(l**2)
    d_x0p0 = np.sqrt( (1/d**3 * da)**2 + (1/2/d**2 * db)**2 + ((3*a /d**4 - b/d**3)**2 * dd**2 ))
    
    # p0 = c/(d**2) - b/(d**3) + a/(d**4)    
    p0 = c/(d**2) - b/(d**3)/l + a/(d**4)/(l**2)
    d_p0 = np.sqrt( (1/d**4 * da)**2 + (1/d**3 * db)**2 + (1/d**2 * dc)**2 + ((-4*a /d**5 + 3*b/d**4 - 2*c/d**3)**2 * dd**2) )
    
    print(x0, p0)
    em = emittance(x0,p0,x0p0) *1E6 #*np.pi         # calculation fromm mm mrad to m rad for norm_e
    em_err = emit_error(x0,p0,x0p0,d_x0,d_p0,d_x0p0) *1E6
    # print(perr)
    
    # par,pcov = curve_fit(full_function, current, mean_beam_width, sigma=std, absolute_sigma=False, p0=[0.001,0.001,0.001])
    # perr = np.sqrt(np.diag(pcov))
    # print(perr)
    # em = emittance(*par) *1E6 #*np.pi
    # em_err = emit_error(x0,p0,x0p0,d_x0,d_p0,d_x0p0) *1E6
    
    # em2 = emittance(x0,p0,x0p0)**2
    # em2_err = emit2_error(x0,p0,x0p0,d_x0,d_p0,d_x0p0)
    
    # print('emit^2:', em2, em2_err)
    # print(d_x0/x0, d_x0p0/x0p0, d_p0/p0)
    # print( em_err*1E-6 * (em*1E-6))
    
    # print(em, em_err)
    
    norm_e = norm_floet(em,E)
    norm_e_err = norm_floet(em+em_err,E)
    # print("emittance: ", em, "\n", "norm_emit: ", norm_e)
    
        
    if plotting:
        fig, ax = plt.subplots(1,1,figsize=[9,7])
        ax.errorbar(current,mean_beam_width,#*1E6,
                     yerr=std,#*1E6,
                     fmt='.', label="measured data")
        x_plot = np.arange(min(x),max(x),0.01)
        # plt.errorbar(x_plot,f(x_plot,*par), label="fit")
        ax.errorbar(current,full_function(current,*par), label="fit")
        ax.set_xlabel(r"quadrupole strength in m$^{-2}$")
        if direct == 'hor':
            ax.set_ylabel(r"$\sigma_x^2$ in mm$^2$")
        if direct == 'ver':
            ax.set_ylabel(r"$\sigma_y^2$ in mm$^2$")
        
        plt.legend()
        plt.tight_layout()
        
        # plt.text((x.mean()-x.min())/2+x.min(),mean_beam_width.max()*1E6,"emittance = {:.3g} mm*mrad".format(em))
    # return current, mean_beam_width, std, par
    return em*1E-6, em_err*1E-6                 # return to mm mrad
    # return norm_e, norm_e_err


arg_n = len(sys.argv)
arg_s = sys.argv

#        script + Energy + quad min + beam w + beam std 
arg_min= 1      + 1      + 2        + 2      + 2

if arg_n < arg_min:
    print('# Number of parameters \''+str(arg_n)+'\' is less then \''+str(arg_min)+'\'')
    print('# Dummy demo data will be used instead')

    plotting = False
    Energy   = 5.81
    direct   = 'x'

    quad_curr = np.array([ 0.9  , 1.0  , 1.1  ])
    beam_widt = np.array([ 0.51 , 0.52 , 0.53 ])
    beam_std  = np.array([ 0.12 , 0.13 , 0.14 ])
    
else:

    plotting = False
    Energy   = float(arg_s[1])
    direct   = 'x'

    nn = arg_n - 1 - 1
    n  = int(nn/3) 
    
    quad_curr = []
    beam_widt = []
    beam_std  = []

    if nn % 3 != 0:
        print("# WARNING: Expected 3 x "+str(n)+" long array, but it is "+str(nn)+" long array. Truncating to "+str(n*3)+" elements.")
    
    for i in range(0,n):
        quad_curr.append(float(arg_s[2+i]))
        beam_widt.append(float(arg_s[2+i+n]))
        beam_std.append(float(arg_s[2+i+2*n]))
    
    quad_curr = np.array(quad_curr)
    beam_widt = np.array(beam_widt)
    beam_std  = np.array(beam_std)
    
print("# Inputs (data array size "+str(n)+")")
print("# Energy: "+str(Energy))
print("# Q_curr: "+str(quad_curr))
print("# Beam_w: "+str(beam_widt))
print("# Beam_s: "+str(beam_std))

emit = fit_emitance(quad_curr, beam_widt, beam_std, plotting, Energy, direct)

print( 'Emittance,error: ' + str(emit) )

