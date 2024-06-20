import sys,importlib

print('### Short System Information ###')

#print ('Number of arguments: ' + str(len(sys.argv)))
#print ('Argument List: ' + str(sys.argv))

print('\nsys.path:')
print(sys.path)

import scipy
print('\nscipy: '+scipy.__version__)
print(importlib.util.find_spec('scipy'))

import numpy
print('\nnumpy: '+numpy.__version__)
print(importlib.util.find_spec('numpy'))

#print('\nggg:')
#print(ggg.__version__)

